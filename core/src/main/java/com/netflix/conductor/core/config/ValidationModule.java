package com.netflix.conductor.core.config;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.apache.bval.jsr.ApacheValidationProvider;
import org.apache.bval.util.reflection.Reflection;

import javax.validation.Validation;
import javax.validation.Validator;

/**
 * Most of the constraints validators are define at data model
 * but there custom validators which requires access to DAO which
 * is not possible in common layer.
 * This class defines programmatic constraints validators defined
 * on WokflowTask which accesses MetadataDao to check if TaskDef
 * exists in store or not.
 *
 * @author fjhaveri
 */

public class ValidationModule extends AbstractModule {
    /**
     * Custom validation mapping configuration for constraints defined in core module.
     */
    private static final String VALIDATION_MAPPING_FILE = "conductor-validation-mapping.xml";

    @Override
    protected void configure() {
    }

    @Provides
    @Singleton
    public Validator getValidator() {
        return Validation.byProvider(ApacheValidationProvider.class)
                .configure()
                .addMapping(Reflection.loaderFromThreadOrClass(this.getClass()).getResourceAsStream(VALIDATION_MAPPING_FILE))
                .buildValidatorFactory()
                .getValidator();
    }
}
