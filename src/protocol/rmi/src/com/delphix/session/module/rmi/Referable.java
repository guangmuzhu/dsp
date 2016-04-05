package com.delphix.session.module.rmi;

import java.util.UUID;

/**
 * The interface is for nested RMI which means
 * a return value can be another remote object
 * reference and its methods can be invoked via
 * RMI again.
 * <p/>
 * If a RMI method wants to return a type that
 * can be remote invoked, the return type should
 * implement the Referable interface, and
 * Serializable interface of course. The RMI server
 * will save the return value in the `exportedObjects'
 * and set the `objectId' by calling `setObjectId()'.
 * The RMI server should get the `objectId' via
 * `getObjectId()' and create a `ObjectCreateResponse'
 * instance according to the `objectId'.
 * <p/>
 * PS: the return type MUST be a interface.
 */
public interface Referable {
    UUID getObjectId();

    void setObjectId(UUID objectId);
}
