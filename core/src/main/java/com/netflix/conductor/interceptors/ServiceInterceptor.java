package com.netflix.conductor.interceptors;

import com.google.inject.Inject;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import javax.inject.Provider;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validator;
import javax.validation.executable.ExecutableValidator;
import java.lang.reflect.Modifier;
import java.util.Set;

/**
 * Intercept method calls annotated with {@link com.netflix.conductor.annotations.Service}
 * and runs hibernate validations on it.
 *
 */
public class ServiceInterceptor implements MethodInterceptor{

    private Provider<Validator> validatorProvider;

    @Inject
    public ServiceInterceptor(Provider<Validator> validator) {
        this.validatorProvider = validator;
    }

    /**
     *
     * @param invocation
     * @return
     * @throws ConstraintViolationException incase of any constraints
     * defined on method parameters are violated.
     */
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {

        if (skipMethod(invocation)) {
            return invocation.proceed();
        }

        ExecutableValidator executableValidator = validatorProvider.get().forExecutables();

        Set<ConstraintViolation<Object>> result = executableValidator.validateParameters(
                invocation.getThis(), invocation.getMethod(), invocation.getArguments());

        if (!result.isEmpty()) {
            throw new ConstraintViolationException(result);
        }

        return invocation.proceed();
    }

    private boolean skipMethod(MethodInvocation invocation) {
        // skip non-public methods or methods on Object class.
        return !Modifier.isPublic( invocation.getMethod().getModifiers() ) || invocation.getMethod().getDeclaringClass().equals( Object.class );
    }
}
