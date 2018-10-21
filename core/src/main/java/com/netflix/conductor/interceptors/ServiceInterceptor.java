package com.netflix.conductor.interceptors;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.core.execution.ApplicationException;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;


import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import javax.inject.Provider;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import javax.validation.executable.ExecutableValidator;


public class ServiceInterceptor implements MethodInterceptor{

//    @Inject
    private Provider<Validator> validatorProvider;

    @Inject
    public ServiceInterceptor(Provider<Validator> validator) {
        this.validatorProvider = validator;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {

        if (skipMethod( invocation )) {
            return invocation.proceed();
        }

        Method method = invocation.getMethod();
        Object[] args = invocation.getArguments();

        //ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        //Validator validator = factory.getValidator();
        ExecutableValidator executableValidator = validatorProvider.get().forExecutables();

        Set<ConstraintViolation<Object>> result = executableValidator.validateParameters(
                invocation.getThis(), invocation.getMethod(), invocation.getArguments());

        if (!result.isEmpty()) {
            throw new ConstraintViolationException(result);
        }


        for(int i=0; i < args.length; i++) {
            if (args[i] instanceof List) {
                List<?> list = (List<?>) args[i];

                String type = args[i].getClass().getTypeName();
                list.stream().forEach(t -> {

                    Set<ConstraintViolation<Object>> results = validatorProvider.get().validate(t);

                    if (!result.isEmpty()) {
                        throw new ConstraintViolationException(results);
                    }
                });
            }
        }

        return invocation.proceed();
    }

//    @Inject
//    private Validator validator;
//
//    @AroundInvoke
//    public Object validateMethodInvocation(InvocationContext ctx) throws Exception {
//        Set<ConstraintViolation<Object>> violations;
//        ExecutableValidator executableValidator = validator.forExecutables();
//        violations = executableValidator.validateParameters( ctx.getTarget(), ctx.getMethod(), ctx.getParameters() );
//        processViolations( violations );
//        Object result = ctx.proceed();
//        violations = executableValidator.validateReturnValue( ctx.getTarget(), ctx.getMethod(), result );
//        processViolations( violations );
//        return result;
//    }
//
//    private void processViolations(Set<ConstraintViolation<Object>> violations) {
//        violations.stream().map( ConstraintViolation::getMessage ).forEach( System.out::println );
//    }
//

    private boolean skipMethod(MethodInvocation invocation) {
        // skip non-public methods or methods on Object class.
        return !Modifier.isPublic( invocation.getMethod().getModifiers() ) || invocation.getMethod().getDeclaringClass().equals( Object.class );
    }
}
