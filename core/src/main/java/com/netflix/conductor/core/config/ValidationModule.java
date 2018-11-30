package com.netflix.conductor.core.config;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.MultibindingsScanner;
import com.google.inject.multibindings.ProvidesIntoSet;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.hibernate.validator.cfg.ConstraintMapping;
import com.netflix.conductor.validations.TaskDefConstraint;

import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorContext;
import java.util.Set;

public class ValidationModule extends AbstractModule {

    protected void configure() {
        install(MultibindingsScanner.asModule());
    }

    @Provides
    @Singleton
    public HibernateValidatorConfiguration getConfiguration() {
        return Validation.byProvider(HibernateValidator.class).configure();
    }

    @Provides
    @Singleton
    public Validator getValidator(ValidatorContext validatorContext) {
        return validatorContext.getValidator();
    }

    @Provides
    @Singleton
    public ValidatorContext getValidatorContext(HibernateValidatorConfiguration configuration, Set<ConstraintMapping> constraintMappings) {
        constraintMappings.forEach(configuration::addMapping);
        return configuration.buildValidatorFactory()
                .usingContext();
    }

    @ProvidesIntoSet
    public ConstraintMapping getWorkflowTaskConstraint(final HibernateValidatorConfiguration configuration) {
        return TaskDefConstraint.getWorkflowTaskConstraint(configuration);
    }

    @ProvidesIntoSet
    public ConstraintMapping getWorkflowDefConstraint(final HibernateValidatorConfiguration configuration) {
        return TaskDefConstraint.getWorkflowDefConstraint(configuration);
    }

}
