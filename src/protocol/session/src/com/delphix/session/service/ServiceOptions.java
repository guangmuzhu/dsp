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

package com.delphix.session.service;

import org.apache.commons.lang.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * This class describes a collection of service options and their associated values. In addition to the basic getter
 * and setter, it supports the operations required for protocol negotiation, such as propose, negotiate, override,
 * encode, and decode. It also supports automatic completion of the set of options explicitly customized by the user
 * using predefined defaults. Finally it allows the separation of options by scope.
 */
public class ServiceOptions {

    protected final Map<ServiceOption<?>, Object> options = new HashMap<ServiceOption<?>, Object>();

    /**
     * Get the value for the given option and it may be null.
     */
    @SuppressWarnings("unchecked")
    public <T> T getOption(ServiceOption<T> option) {
        return (T) options.get(option);
    }

    /**
     * Set the option with the given value and override the existing one if any.
     */
    public <T> void setOption(ServiceOption<T> option, T value) {
        option.validate(value);
        options.put(option, value);
    }

    /**
     * Construct a protocol options proposal for negotiation with the peer.
     */
    public ServiceOptions propose() {
        ServiceOptions proposal = new ServiceOptions();
        Set<ServiceOption<?>> specified = options.keySet();

        for (ServiceOption<?> option : specified) {
            if (!option.isLocal()) {
                proposal.options.put(option, getOption(option));
            }
        }

        return proposal;
    }

    /**
     * Derive the protocol options based on the existing offer and the peer proposal.
     */
    public ServiceOptions negotiate(ServiceOptions proposal) {
        ServiceOptions result = new ServiceOptions();
        Set<ServiceOption<?>> proposed = proposal.options.keySet();

        for (ServiceOption<?> option : proposed) {
            negotiate(proposal, option, result);
        }

        return result;
    }

    /**
     * Override the protocol options (such as from protocol negotiation) on top of the existing one.
     */
    public void override(ServiceOptions result) {
        Set<ServiceOption<?>> specified = result.options.keySet();

        for (ServiceOption<?> option : specified) {
            setOption(result, option);
        }
    }

    /**
     * Get the nexus scoped service options.
     */
    public ServiceOptions getNexusOptions() {
        ServiceOptions nexus = new ServiceOptions();
        Set<ServiceOption<?>> specified = options.keySet();

        for (ServiceOption<?> option : specified) {
            if (option.isNexus()) {
                nexus.options.put(option, getOption(option));
            }
        }

        return nexus;
    }

    /**
     * Get the transport scoped service options.
     */
    public ServiceOptions getTransportOptions() {
        ServiceOptions xport = new ServiceOptions();
        Set<ServiceOption<?>> specified = options.keySet();

        for (ServiceOption<?> option : specified) {
            if (!option.isNexus()) {
                xport.options.put(option, getOption(option));
            }
        }

        return xport;
    }

    /**
     * Return true if the options is complete.
     */
    public boolean isComplete() {
        Set<ServiceOption<?>> specified = options.keySet();
        Set<ServiceOption<?>> supported = ServiceOption.supportedOptions();

        return specified.equals(supported);
    }

    public Map<String, String> values() {
        Set<ServiceOption<?>> optionSet = options.keySet();
        Map<String, String> values = new HashMap<String, String>();

        for (ServiceOption<?> option : optionSet) {
            values.put(option.getName(), encode(option));
        }

        return values;
    }

    @Override
    public String toString() {
        Set<ServiceOption<?>> optionSet = options.keySet();
        StringBuilder builder = new StringBuilder();

        for (ServiceOption<?> option : optionSet) {
            if (builder.length() > 0) {
                builder.append(';');
            }

            String string = encode(option);

            builder.append(option.getName());
            builder.append('=');
            builder.append(string);
        }

        return builder.toString();
    }

    public static ServiceOptions fromString(String string) {
        ServiceOptions options = new ServiceOptions();

        String[] pairs = StringUtils.split(string, ';');

        for (String pair : pairs) {
            String[] tokens = StringUtils.split(pair, '=');

            ServiceOption<?> option = ServiceOption.getOption(tokens[0]);
            options.decode(option, tokens[1]);
        }

        return options;
    }

    /**
     * Create a client service options template.
     */
    public static ServiceOptions getClientOptions() {
        return new ServiceClientOptions();
    }

    /**
     * Create a server service options template.
     */
    public static ServiceOptions getServerOptions() {
        return new ServiceServerOptions();
    }

    private <T> String encode(ServiceOption<T> option) {
        T value = getOption(option);
        return option.encode(value);
    }

    private <T> void decode(ServiceOption<T> option, String string) {
        T value = option.decode(string);
        setOption(option, value);
    }

    private <T> void negotiate(ServiceOptions proposal, ServiceOption<T> option, ServiceOptions result) {
        T proposed = proposal.getOption(option);
        T offered = getOption(option);

        if (offered == null) {
            throw new IllegalArgumentException("option not offered for negotiation " + option.getName());
        }

        T value;

        if (proposed == null) {
            value = offered;
        } else {
            value = option.negotiate(offered, proposed);
        }

        result.setOption(option, value);
    }

    private <T> void setOption(ServiceOptions result, ServiceOption<T> option) {
        T value = result.getOption(option);
        setOption(option, value);
    }

    private static class ServiceClientOptions extends ServiceOptions {

        public ServiceClientOptions() {
            Set<ServiceOption<?>> specified = options.keySet();

            Set<ServiceOption<?>> addition = ServiceOption.clientOptions();
            Set<ServiceOption<?>> deletion = ServiceOption.serverOptions();

            specified.remove(deletion);
            addition.removeAll(specified);

            for (ServiceOption<?> option : addition) {
                if (option.hasDefault()) {
                    options.put(option, option.getDefault());
                }
            }
        }

        /**
         * Set the option with the given value and override the existing one if any.
         */
        @Override
        public <T> void setOption(ServiceOption<T> option, T value) {
            if (!option.isClient()) {
                throw new IllegalArgumentException("option not allowed " + option.getName());
            }

            super.setOption(option, value);
        }

        /**
         * Return true if the options is complete.
         */
        @Override
        public boolean isComplete() {
            Set<ServiceOption<?>> specified = options.keySet();
            Set<ServiceOption<?>> supported = ServiceOption.clientOptions();

            return specified.equals(supported);
        }
    }

    private static class ServiceServerOptions extends ServiceOptions {

        public ServiceServerOptions() {
            Set<ServiceOption<?>> specified = options.keySet();

            Set<ServiceOption<?>> addition = ServiceOption.serverOptions();
            Set<ServiceOption<?>> deletion = ServiceOption.clientOptions();

            specified.remove(deletion);
            addition.removeAll(specified);

            for (ServiceOption<?> option : addition) {
                if (option.hasDefault()) {
                    options.put(option, option.getDefault());
                }
            }
        }

        /**
         * Set the option with the given value and override the existing one if any.
         */
        @Override
        public <T> void setOption(ServiceOption<T> option, T value) {
            if (!option.isServer()) {
                throw new IllegalArgumentException("option not allowed " + option.getName());
            }

            super.setOption(option, value);
        }

        /**
         * Return true if the options is complete.
         */
        @Override
        public boolean isComplete() {
            Set<ServiceOption<?>> specified = options.keySet();
            Set<ServiceOption<?>> supported = ServiceOption.serverOptions();

            return specified.equals(supported);
        }
    }
}
