package com.netflix.conductor.server;

import com.google.inject.Inject;
import com.google.inject.Provider;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.hibernate.validator.cfg.ConstraintMapping;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.executable.ExecutableValidator;
import javax.validation.metadata.BeanDescriptor;
import java.util.Set;

public class ValidatorProvider implements Provider<Validator> {

    private Set<ConstraintMapping> constraintMappings;

    @Inject
    public ValidatorProvider( Set<ConstraintMapping> constraintMappings) {
        this.constraintMappings = constraintMappings;
    }

    @Override
    public Validator get() {

      HibernateValidatorConfiguration hibernateValidatorConfiguration = Validation.byProvider(HibernateValidator.class).configure();
       constraintMappings.forEach(hibernateValidatorConfiguration::addMapping);
      return hibernateValidatorConfiguration.buildValidatorFactory().getValidator();
    }
}
