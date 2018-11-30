package com.netflix.conductor.validations;

import org.hibernate.validator.cfg.ConstraintDef;

public class CheckTaskDefExistsConstraintDef extends ConstraintDef<CheckTaskDefExistsConstraintDef, CheckTaskDefExists> {

    public CheckTaskDefExistsConstraintDef() {
        super(CheckTaskDefExists.class);
    }
}
