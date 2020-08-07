package com.netflix.conductor.server;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * Intercepts all methods in class path annotated with @FaultInjectionInterceptor,
 * and evaluate based on the current DynamicFailureProbability conditions provided.
 *
 * This interceptor is intended for unit / integration testing, which allows to inject failure at a certain point,
 * as an alternative to Mocking / Stubbing. For eg., instead of Mocking entire persistence object,
 * we can continue to use embedded implementations, while probabilistically failing certain operations if/when required.
 */
public class FaultInjectionWithProbability implements MethodInterceptor {

    DynamicFailureProbability failureProbability;

    public FaultInjectionWithProbability(DynamicFailureProbability failureProbability) {
        this.failureProbability = failureProbability;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        if (
                (invocation.getMethod().getName().equals(failureProbability.getMethodName()) || "*".equals(failureProbability.getMethodName())) &&
                (failureProbability.get() > 0 && (Math.random() <= failureProbability.get()))
        ) {
            throw new IllegalStateException(
                    invocation.getMethod().getName() + " failed with provided probability: " + failureProbability.get());
        } else {
            return invocation.proceed();
        }
    }

    public class DynamicFailureProbability {
        private String methodName;
        private double failureProbability = 0;

        public double get() {
            return failureProbability;
        }

        public void setFailureProbability(double failureProbability) {
            this.failureProbability = failureProbability;
        }

        public String getMethodName() {
            return methodName;
        }

        public void setMethodName(String methodName) {
            this.methodName = methodName;
        }
    }
}
