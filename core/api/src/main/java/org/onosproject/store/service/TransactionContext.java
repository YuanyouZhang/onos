/*
 * Copyright 2015 Open Networking Laboratory
 *
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

package org.onosproject.store.service;

/**
 * Provides a context for transactional operations.
 * <p>
 * A transaction context provides a boundary within which transactions
 * are run. It also is a place where all modifications made within a transaction
 * are cached until the point when the transaction commits or aborts. It thus ensures
 * isolation of work happening with in the transaction boundary.
 * <p>
 * A transaction context is a vehicle for grouping operations into a unit with the
 * properties of atomicity, isolation, and durability. Transactions also provide the
 * ability to maintain an application's invariants or integrity constraints,
 * supporting the property of consistency. Together these properties are known as ACID.
 */
public interface TransactionContext {

    /**
     * Returns if this transaction context is open.
     * @return true if open, false otherwise.
     */
    boolean isOpen();

    /**
     * Starts a new transaction.
     */
    void begin();

    /**
     * Commits a transaction that was previously started thereby making its changes permanent
     * and externally visible.
     * @throws TransactionException if transaction fails to commit.
     */
    void commit();

    /**
     * Rolls back the current transaction, discarding all its changes.
     */
    void rollback();

    /**
     * Creates a new transactional map.
     * @param mapName name of the transactional map.
     * @param serializer serializer to use for encoding/decoding keys and vaulues.
     * @return new Transactional Map.
     */
    <K, V> TransactionalMap<K, V> createTransactionalMap(String mapName, Serializer serializer);
}