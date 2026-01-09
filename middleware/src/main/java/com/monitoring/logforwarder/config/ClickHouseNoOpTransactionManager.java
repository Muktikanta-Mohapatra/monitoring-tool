package com.monitoring.logforwarder.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * No-operation transaction manager for ClickHouse database.
 *
 * <p><b>Purpose:</b> Provides a transaction manager implementation that satisfies Spring's
 * transaction infrastructure requirements while acknowledging that ClickHouse does not
 * support traditional ACID transactions.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>All transaction operations are no-ops (begin, commit, rollback do nothing)</li>
 *   <li>Manages EntityManager lifecycle for proper resource cleanup</li>
 *   <li>Required by Spring Data JPA even though ClickHouse is non-transactional</li>
 *   <li>Nested transactions are not allowed</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 * @see ClickHouseDataSourceConfig
 */
public class ClickHouseNoOpTransactionManager extends AbstractPlatformTransactionManager {

    private final EntityManagerFactory entityManagerFactory;

    public ClickHouseNoOpTransactionManager(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
        setNestedTransactionAllowed(false);
    }

    @Override
    protected Object doGetTransaction() throws TransactionException {
        ClickHouseTransactionObject txObject = new ClickHouseTransactionObject();
        EntityManagerHolder emHolder = (EntityManagerHolder) 
            TransactionSynchronizationManager.getResource(entityManagerFactory);
        txObject.setEntityManagerHolder(emHolder);
        return txObject;
    }

    @Override
    protected boolean isExistingTransaction(Object transaction) throws TransactionException {
        return false;
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) throws TransactionException {
        ClickHouseTransactionObject txObject = (ClickHouseTransactionObject) transaction;

        if (!txObject.hasEntityManager()) {
            EntityManager em = entityManagerFactory.createEntityManager();
            txObject.setEntityManagerHolder(new EntityManagerHolder(em));
            txObject.setNewEntityManager(true);
            TransactionSynchronizationManager.bindResource(entityManagerFactory, txObject.getEntityManagerHolder());
        }
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) throws TransactionException {
        // No-op: ClickHouse auto-commits each statement, no explicit flush needed
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) throws TransactionException {
        ClickHouseTransactionObject txObject = (ClickHouseTransactionObject) status.getTransaction();
        EntityManager em = txObject.getEntityManagerHolder().getEntityManager();
        if (em != null && em.isOpen()) {
            em.clear();
        }
    }

    @Override
    protected void doSetRollbackOnly(DefaultTransactionStatus status) throws TransactionException {
        ClickHouseTransactionObject txObject = (ClickHouseTransactionObject) status.getTransaction();
        txObject.setRollbackOnly(true);
    }

    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
        ClickHouseTransactionObject txObject = (ClickHouseTransactionObject) transaction;

        if (txObject.isNewEntityManager()) {
            TransactionSynchronizationManager.unbindResourceIfPossible(entityManagerFactory);
            EntityManager em = txObject.getEntityManagerHolder().getEntityManager();
            if (em != null && em.isOpen()) {
                EntityManagerFactoryUtils.closeEntityManager(em);
            }
        }
    }

    private static class ClickHouseTransactionObject {
        private EntityManagerHolder entityManagerHolder;
        private boolean newEntityManager;
        private boolean rollbackOnly;

        public EntityManagerHolder getEntityManagerHolder() {
            return entityManagerHolder;
        }

        public void setEntityManagerHolder(EntityManagerHolder entityManagerHolder) {
            this.entityManagerHolder = entityManagerHolder;
        }

        public boolean hasEntityManager() {
            return entityManagerHolder != null && entityManagerHolder.getEntityManager() != null;
        }

        public boolean isNewEntityManager() {
            return newEntityManager;
        }

        public void setNewEntityManager(boolean newEntityManager) {
            this.newEntityManager = newEntityManager;
        }

        public boolean isRollbackOnly() {
            return rollbackOnly;
        }

        public void setRollbackOnly(boolean rollbackOnly) {
            this.rollbackOnly = rollbackOnly;
        }
    }
}
