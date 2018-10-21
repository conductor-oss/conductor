package com.netflix.conductor.validations;

import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoMap;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.hibernate.validator.cfg.ConstraintMapping;

import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;

import static java.lang.annotation.ElementType.FIELD;

public class TaskDefConstraint {

    @Singleton
    @ProvidesIntoSet
    public static ConstraintMapping getTaskDefConstraint(final HibernateValidatorConfiguration configuration) {
        ConstraintMapping mapping = configuration.createConstraintMapping();

        mapping.type( TaskDef.class )
                .property( "name", FIELD )
                .constraint( new TaskDefNameConstraintDef() );

        return mapping;
    }
}
