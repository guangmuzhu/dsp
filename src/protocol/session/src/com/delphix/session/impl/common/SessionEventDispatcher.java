/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Copyright (c) 2013 by Delphix. All rights reserved.
 */

package com.delphix.session.impl.common;

import com.delphix.session.service.NexusListener;
import com.delphix.session.util.Event;
import com.delphix.session.util.EventManager;

import java.util.ArrayList;
import java.util.List;

/**
 * This class describes the session event dispatcher. It manages a list of session event listeners on behalf of the
 * event source and handles the interaction between the source and the event manager for event delivery purpose.
 *
 * A session event describes a state change. Session event processing must satisfy the following requirements.
 *
 *   - No state change notifications should be missed after the listeners is added
 *   - All state change notifications must be delivered in the same order as they occurred
 *   - Session event listener must not be removed when a state change notification is being delivered
 *
 * When an event listener is registered with an existing session, we may expect a notification to be delivered based
 * on the current session state instead of a state transition. It is important to note that the "current" session
 * state refers to the state at the time of the listener registration, not when the registration event is processed.
 * Otherwise, we may violate the event processing constraints as illustrated in the following timing sequence.
 *
 *   time    event     description
 *   ----    -----     -------------------------------------
 *    1      N         new listener registration (current state A)
 *    2      A->B      source state changes from A to B (current state B)
 *    3      B->C      source state changes from B to C (current state C)
 *    4      f(N)      event manager processes N with current source state C
 *    5      f(A->B)   event manager processes A->B
 *
 * The effect above is that the world sees the source first in state C then A then B, which is different from what
 * really happened.
 *
 * To deliver a registration event safely, one of the following requirements must be satisfied.
 *
 *  1) find out the state of the source when N is enqueued
 *  2) make sure there are no events from the same source when N is processed
 *  3) process all events from the same source before N
 *
 * If an event source doesn't meet one of the three requirements, it must not notify a listener on new registration.
 * Instead, it must wait until an event is generated from state transition. And this is the case with the current
 * session implementation.
 */
public class SessionEventDispatcher {

    // Newly added session event listeners that have not yet fired
    private final List<NexusListener> newListeners = new ArrayList<NexusListener>();

    // Session event listeners that have already fired at least once
    private final List<NexusListener> listeners = new ArrayList<NexusListener>();

    private final EventManager manager; // Session event manager
    private final SessionEventSource source; // Session event source

    private boolean inProgress; // Notification in progress

    public SessionEventDispatcher(SessionEventSource source, EventManager manager) {
        this.source = source;
        this.manager = manager;
    }

    /**
     * Add the event listener to the dispatcher. The new listener is placed on a separate list and an initial state
     * notification is posted to the session event manager for the newly registered event listener. The notification
     * is delivered with the session state at the time of the delivery.
     */
    public void addListener(NexusListener listener) {
        synchronized (this) {
            if (!newListeners.isEmpty()) {
                newListeners.add(listener);
                return;
            }

            newListeners.add(listener);
        }

        Event event = new Event(source) {

            @Override
            public void run() {
                dispatch();
            }
        };

        manager.execute(event);
    }

    /**
     * Quiesce state change notification before removing the listener to make sure a listener is never removed while
     * an event is being actively dispatched.
     */
    public synchronized void removeListener(NexusListener listener) {
        while (inProgress) {
            try {
                wait();
            } catch (InterruptedException e) {
                // Do nothing
            }
        }

        if (newListeners.remove(listener)) {
            return;
        }

        listeners.remove(listener);
    }

    /**
     * Post an event for dispatching. This is invoked by the event source as new events are generated. The event
     * mechanism ensures events are delivered to the listeners in the same order they are posted by the source.
     */
    public void post(Event event) {
        manager.execute(event);
    }

    /**
     * Dispatch an initial state notification to each of the newly registered session event listeners. Unless they
     * are one-shot, the newly registered listeners are moved to the existing listener list. This is invoked from
     * the session event manager through the event delivery interface.
     */
    private void dispatch() {
        List<NexusListener> notifyList;

        synchronized (this) {
            if (newListeners.isEmpty()) {
                return;
            }

            notifyList = new ArrayList<NexusListener>();
            notifyList.addAll(newListeners);
            newListeners.clear();

            inProgress = true;
        }

        for (NexusListener listener : notifyList) {
            source.notify(listener);
        }

        synchronized (this) {
            for (NexusListener listener : notifyList) {
                if (!listener.isOneShot()) {
                    listeners.add(listener);
                }
            }

            inProgress = false;
            notifyAll();
        }
    }

    /**
     * Dispatch a state change notification to each of the existing session event listeners. This is invoked from the
     * session event manager through the event delivery interface.
     */
    public void dispatch(Event event) {
        List<NexusListener> notifyList;

        synchronized (this) {
            if (listeners.isEmpty()) {
                return;
            }

            notifyList = new ArrayList<NexusListener>();
            notifyList.addAll(listeners);

            inProgress = true;
        }

        for (NexusListener listener : notifyList) {
            source.notify(listener, event);
        }

        synchronized (this) {
            inProgress = false;
            notifyAll();
        }
    }
}
