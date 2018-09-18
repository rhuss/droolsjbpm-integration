package org.kie.processmigration.service.impl;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.security.auth.login.CredentialNotFoundException;
import javax.transaction.Transactional;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.kie.processmigration.model.Credentials;
import org.kie.processmigration.model.Execution.ExecutionType;
import org.kie.processmigration.model.Migration;
import org.kie.processmigration.model.MigrationDefinition;
import org.kie.processmigration.model.MigrationReport;
import org.kie.processmigration.model.Plan;
import org.kie.processmigration.model.exceptions.PlanNotFoundException;
import org.kie.processmigration.service.CredentialsProviderFactory;
import org.kie.processmigration.service.CredentialsService;
import org.kie.processmigration.service.KieService;
import org.kie.processmigration.service.MigrationService;
import org.kie.processmigration.service.PlanService;
import org.kie.processmigration.service.SchedulerService;
import org.kie.server.api.model.admin.MigrationReportInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class MigrationServiceImpl implements MigrationService {

    private static final Logger logger = LoggerFactory.getLogger(MigrationServiceImpl.class);

    @PersistenceContext
    private EntityManager em;

    @Inject
    private PlanService planService;

    @Inject
    private KieService kieService;

    @Inject
    private SchedulerService schedulerService;

    @Inject
    private CredentialsService credentialsService;

    @Override
    public Migration get(Long id) {
        TypedQuery<Migration> query = em.createNamedQuery("Migration.findById", Migration.class);
        query.setParameter("id", id);
        try {
            return query.getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    @Override
    public List<MigrationReport> getResults(Long id) {
        TypedQuery<MigrationReport> query = em.createNamedQuery("MigrationReport.findByMigrationId", MigrationReport.class);
        query.setParameter("id", id);
        return query.getResultList();
    }

    @Override
    public List<Migration> findAll() {
        return em.createNamedQuery("Migration.findAll", Migration.class).getResultList();
    }

    @Override
    @Transactional
    public Migration submit(MigrationDefinition definition, Credentials credentials) {
        Plan plan = planService.get(definition.getPlanId());
        if (plan == null) {
            throw new PlanNotFoundException(definition.getPlanId());
        }
        Migration migration = new Migration(definition);
        em.persist(migration);
        if (ExecutionType.SYNC.equals(definition.getExecution().getType())) {
            credentialsService.save(credentials.setMigrationId(migration.getId()));
            migrate(migration, plan, credentials);
        } else {
            schedulerService.scheduleMigration(migration, credentials);
        }
        return migration;
    }

    @Override
    @Transactional
    public Migration delete(Long id) {
        Migration migration = get(id);
        if (migration == null) {
            return null;
        }
        em.remove(migration);
        return migration;
    }

    @Override
    @Transactional
    public Migration update(Long id, MigrationDefinition definition) {
        Migration migration = get(id);
        if (migration != null) {
            em.persist(migration);
        }
        return migration;
    }

    @Override
    @Transactional
    public Migration migrate(Migration migration) {
        Plan plan = planService.get(migration.getDefinition().getPlanId());
        if (plan == null) {
            throw new PlanNotFoundException(migration.getDefinition().getPlanId());
        }
        Credentials credentials = credentialsService.get(migration.getId());
        return migrate(migration, plan, credentials);
    }

    private Migration migrate(Migration migration, Plan plan, Credentials credentials) {
        try {
            migration = em.find(Migration.class, migration.getId());
            em.persist(migration.start());
            if (credentials == null) {
                throw new CredentialNotFoundException();
            }
            AtomicBoolean hasErrors = new AtomicBoolean(false);
            //each instance id will spawn its own request to KIE server for migration.
            List<Long> instancesIdList = migration.getDefinition().getProcessInstanceIds();
            for (Long instanceId : instancesIdList) {
                MigrationReportInstance reportInstance = kieService
                                                                   .createProcessAdminServicesClient(CredentialsProviderFactory.getProvider(credentials))
                                                                   .migrateProcessInstance(
                                                                                           plan.getSourceContainerId(),
                                                                                           instanceId,
                                                                                           plan.getTargetContainerId(),
                                                                                           plan.getTargetProcessId(),
                                                                                           plan.getMappings());
                if (!hasErrors.get() && !reportInstance.isSuccessful()) {
                    hasErrors.set(Boolean.TRUE);
                }
                em.persist(new MigrationReport(migration.getId(), reportInstance));
            }
            migration.complete(hasErrors.get());
        } catch (Exception e) {
            logger.error("Migration failed", e);
            migration.fail(e);
        } finally {
            credentialsService.delete(migration.getId());
            em.persist(migration);
            if (ExecutionType.ASYNC.equals(migration.getDefinition().getExecution().getType()) &&
                migration.getDefinition().getExecution().getCallbackUrl() != null) {
                doCallback(migration);
            }
        }
        return migration;
    }

    private void doCallback(Migration migration) {
        URI callbackURI = null;
        try {
            callbackURI = migration.getDefinition().getExecution().getCallbackUrl();
            Response response = ClientBuilder.newClient()
                                             .target(callbackURI)
                                             .request(MediaType.APPLICATION_JSON)
                                             .buildPost(Entity.json(migration))
                                             .invoke();
            if (Status.OK.getStatusCode() == response.getStatus()) {
                logger.debug("Migration [{}] - Callback to {} replied successfully", migration.getId(), callbackURI);
            } else {
                logger.warn("Migration [{}] - Callback to {} replied with {}", migration.getId(), callbackURI, response.getStatus());
            }
        } catch (Exception e) {
            logger.error("Migration [{}] - Callback to {} failed.", migration.getId(), callbackURI, e);
        }
    }

}
