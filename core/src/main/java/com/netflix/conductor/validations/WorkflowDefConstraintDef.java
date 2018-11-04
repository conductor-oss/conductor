package com.netflix.conductor.validations;

import org.hibernate.validator.cfg.ConstraintDef;

public class WorkflowDefConstraintDef extends ConstraintDef<WorkflowDefConstraintDef, WorkflowDefConstraint> {

    public WorkflowDefConstraintDef() {
        super( WorkflowDefConstraint.class );
    }

}

