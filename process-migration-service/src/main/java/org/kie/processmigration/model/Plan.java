/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.kie.processmigration.model;

import java.io.Serializable;
import java.util.Map;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.MapKeyColumn;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(name = "plans")
@NamedQueries({
               @NamedQuery(name = "Plan.findAll", query = "SELECT p FROM Plan p"),
               @NamedQuery(name = "Plan.findById", query = "SELECT p FROM Plan p WHERE p.id = :id")
})
public class Plan implements Serializable {

    private static final long serialVersionUID = 1244535648642365858L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String name;

    private String description;

    @JsonProperty("source_container_id")
    @Column(name = "source_container_id")
    private String sourceContainerId;

    @JsonProperty("target_container_id")
    @Column(name = "target_container_id")
    private String targetContainerId;

    @JsonProperty("target_process_id")
    @Column(name = "target_process_id")
    private String targetProcessId;

    @ElementCollection(fetch = FetchType.EAGER)
    @MapKeyColumn(name = "source")
    @Column(name = "target")
    @CollectionTable(
                     name = "plan_mappings",
                     joinColumns = @JoinColumn(name = "plan_id")
    )
    private Map<String, String> mappings;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSourceContainerId() {
        return sourceContainerId;
    }

    public void setSourceContainerId(String sourceContainerId) {
        this.sourceContainerId = sourceContainerId;
    }

    public String getTargetContainerId() {
        return targetContainerId;
    }

    public void setTargetContainerId(String targetContainerId) {
        this.targetContainerId = targetContainerId;
    }

    public String getTargetProcessId() {
        return targetProcessId;
    }

    public void setTargetProcessId(String targetProcessId) {
        this.targetProcessId = targetProcessId;
    }

    public Map<String, String> getMappings() {
        return mappings;
    }

    public void setMappings(Map<String, String> mappings) {
        this.mappings = mappings;
    }

}
