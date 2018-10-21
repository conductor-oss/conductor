package com.netflix.conductor.validations;

import org.hibernate.validator.cfg.ConstraintDef;

public class TaskDefNameConstraintDef extends ConstraintDef<TaskDefNameConstraintDef, MetadataConstraints> {

    public TaskDefNameConstraintDef() {
        super(MetadataConstraints.class);
    }
}