package com.netflix.conductor.validations;

import org.hibernate.validator.cfg.ConstraintDef;

public class CheckTaskDefNotExistsConstraintDef extends ConstraintDef<CheckTaskDefNotExistsConstraintDef, CheckTaskDefNotExists> {

    public CheckTaskDefNotExistsConstraintDef() {
        super(CheckTaskDefNotExists.class);
    }
}
