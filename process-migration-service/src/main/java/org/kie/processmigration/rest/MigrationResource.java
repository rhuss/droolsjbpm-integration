/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package org.kie.processmigration.rest;

import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.kie.processmigration.model.Credentials;
import org.kie.processmigration.model.Execution.ExecutionType;
import org.kie.processmigration.model.Migration;
import org.kie.processmigration.model.MigrationDefinition;
import org.kie.processmigration.model.MigrationReport;
import org.kie.processmigration.service.CredentialsProviderFactory;
import org.kie.processmigration.service.MigrationService;

@Path("/migrations")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class MigrationResource {

    @Inject
    private MigrationService migrationService;

    @GET
    @Consumes({MediaType.APPLICATION_JSON})
    public Response findAll() {
        return Response.ok(migrationService.findAll()).build();
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") Long id) {
        Migration migration = migrationService.get(id);
        if (migration == null) {
            return getMigrationNotFound(id);
        }
        return Response.ok(migration).build();
    }

    @GET
    @Path("/{id}/results")
    public Response getResults(@PathParam("id") Long id) {
        List<MigrationReport> results = migrationService.getResults(id);
        if (results == null) {
            return getMigrationNotFound(id);
        }
        return Response.ok(results).build();
    }

    @POST
    @Consumes({MediaType.APPLICATION_JSON})
    public Response submit(@Context HttpHeaders headers, MigrationDefinition migration) {
        Credentials credentials = CredentialsProviderFactory.getCredentials(headers.getHeaderString(HttpHeaders.AUTHORIZATION));
        if (credentials == null) {
            return Response.status(Status.UNAUTHORIZED).header(HttpHeaders.WWW_AUTHENTICATE, "Basic").build();
        }
        if (ExecutionType.ASYNC.equals(migration.getExecution().getType())) {
            return Response.accepted(migrationService.submit(migration, credentials)).build();
        } else {
            return Response.ok(migrationService.submit(migration, credentials)).build();
        }
    }

    @PUT
    @Path("/{id}")
    @Consumes({MediaType.APPLICATION_JSON})
    public Response update(@PathParam("id") Long id, MigrationDefinition migrationDefinition) {
        // TODO: Support reschedule migrations
        Migration migration = migrationService.update(id, migrationDefinition);
        if (migration == null) {
            return getMigrationNotFound(id);
        } else {
            return Response.ok(migration).build();
        }
    }

    @DELETE
    @Path("/{id}")
    @Consumes({MediaType.APPLICATION_JSON})
    public Response delete(@PathParam("id") Long id) {
        Migration migration = migrationService.delete(id);
        if (migration == null) {
            return getMigrationNotFound(id);
        }
        return Response.ok(migration).build();
    }

    private Response getMigrationNotFound(Long id) {
        return Response.status(Status.NOT_FOUND)
                       .entity(String.format("{\"message\": \"Migration with id %s does not exist\"}", id)).build();
    }

}
