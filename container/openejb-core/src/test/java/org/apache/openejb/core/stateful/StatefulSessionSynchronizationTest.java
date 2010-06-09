/**
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.openejb.core.stateful;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.ejb.AfterBegin;
import javax.ejb.AfterCompletion;
import javax.ejb.BeforeCompletion;
import javax.ejb.EJBException;
import javax.ejb.Local;
import javax.ejb.SessionSynchronization;
import javax.ejb.Stateful;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.interceptor.InvocationContext;
import javax.naming.InitialContext;

import junit.framework.TestCase;

import org.apache.openejb.assembler.classic.Assembler;
import org.apache.openejb.assembler.classic.EjbJarInfo;
import org.apache.openejb.assembler.classic.ProxyFactoryInfo;
import org.apache.openejb.assembler.classic.SecurityServiceInfo;
import org.apache.openejb.assembler.classic.TransactionServiceInfo;
import org.apache.openejb.client.LocalInitialContextFactory;
import org.apache.openejb.config.ConfigurationFactory;
import org.apache.openejb.jee.AssemblyDescriptor;
import org.apache.openejb.jee.EjbJar;
import org.apache.openejb.jee.Interceptor;
import org.apache.openejb.jee.InterceptorBinding;
import org.apache.openejb.jee.NamedMethod;
import org.apache.openejb.jee.StatefulBean;

/**
 * @version $Rev$ $Date$
 */
public class StatefulSessionSynchronizationTest extends TestCase {

    private static final List<Call> result = new ArrayList<Call>();

    public void test() throws Exception {
        System.setProperty(javax.naming.Context.INITIAL_CONTEXT_FACTORY, LocalInitialContextFactory.class.getName());
        Assembler assembler = new Assembler();
        ConfigurationFactory config = new ConfigurationFactory();

        assembler.createProxyFactory(config.configureService(ProxyFactoryInfo.class));
        assembler.createTransactionManager(config.configureService(TransactionServiceInfo.class));
        assembler.createSecurityService(config.configureService(SecurityServiceInfo.class));

        EjbJar ejbJar = new EjbJar();
        AssemblyDescriptor assemblyDescriptor = ejbJar.getAssemblyDescriptor();

        Interceptor interceptor = new Interceptor(SimpleInterceptor.class);
        ejbJar.addInterceptor(interceptor);

        //Test SessionSynchronization interface
        StatefulBean subBeanA = new StatefulBean(SubBeanA.class);
        ejbJar.addEnterpriseBean(subBeanA);
        assemblyDescriptor.addInterceptorBinding(new InterceptorBinding(subBeanA, interceptor));

        //Test configure session synchronization callback methods in deployment plan
        StatefulBean subBeanB = new StatefulBean(SubBeanB.class);
        subBeanB.setAfterBeginMethod(new NamedMethod(SubBeanB.class.getDeclaredMethod("afterBegin")));
        subBeanB.setBeforeCompletionMethod(new NamedMethod(SubBeanB.class.getDeclaredMethod("beforeCompletion")));
        subBeanB.setAfterCompletionMethod(new NamedMethod(SubBeanB.class.getDeclaredMethod("afterCompletion", boolean.class)));
        ejbJar.addEnterpriseBean(subBeanB);
        assemblyDescriptor.addInterceptorBinding(new InterceptorBinding(subBeanB, interceptor));

        //Test session synchronization methods via annotations
        StatefulBean subBeanC = new StatefulBean(SubBeanC.class);
        ejbJar.addEnterpriseBean(subBeanC);
        assemblyDescriptor.addInterceptorBinding(new InterceptorBinding(subBeanC, interceptor));

        //Test override the annotations by deployment plan
        StatefulBean subBeanD = new StatefulBean(SubBeanD.class);
        subBeanD.setAfterBeginMethod(new NamedMethod(SubBeanD.class.getDeclaredMethod("afterBeginNew")));
        subBeanD.setBeforeCompletionMethod(new NamedMethod(SubBeanD.class.getDeclaredMethod("beforeCompletionNew")));
        subBeanD.setAfterCompletionMethod(new NamedMethod(SubBeanD.class.getDeclaredMethod("afterCompletionNew", boolean.class)));
        ejbJar.addEnterpriseBean(subBeanD);
        assemblyDescriptor.addInterceptorBinding(new InterceptorBinding(subBeanD, interceptor));

        //Test only one session synchronization method @AfterBegin
        StatefulBean subBeanE = new StatefulBean(SubBeanE.class);
        ejbJar.addEnterpriseBean(subBeanE);
        assemblyDescriptor.addInterceptorBinding(new InterceptorBinding(subBeanE, interceptor));

        //Test only one session synchronization method @AfterCompletion
        StatefulBean subBeanF = new StatefulBean(SubBeanF.class);
        ejbJar.addEnterpriseBean(subBeanF);
        assemblyDescriptor.addInterceptorBinding(new InterceptorBinding(subBeanF, interceptor));

        //Test only one session synchronization method @BeforeCompletion
        StatefulBean subBeanG = new StatefulBean(SubBeanG.class);
        ejbJar.addEnterpriseBean(subBeanG);
        assemblyDescriptor.addInterceptorBinding(new InterceptorBinding(subBeanG, interceptor));

        EjbJarInfo ejbJarInfo = config.configureApplication(ejbJar);
        assembler.createApplication(ejbJarInfo);
        InitialContext context = new InitialContext();

        List<Call> expectedResult = Arrays.asList(Call.INTERCEPTOR_AFTER_BEGIN, Call.BEAN_AFTER_BEGIN, Call.INTERCEPTOR_BEFORE_COMPLETION, Call.BEAN_BEFORE_COMPLETION,
                Call.INTERCEPTOR_AFTER_COMPLETION, Call.BEAN_AFTER_COMPLETION);

        {
            BeanInterface beanA = (BeanInterface) context.lookup("SubBeanALocal");
            beanA.simpleMethod();
            assertEquals(expectedResult, result);
            result.clear();
        }

        {
            BeanInterface beanB = (BeanInterface) context.lookup("SubBeanBLocal");
            beanB.simpleMethod();
            assertEquals(expectedResult, result);
            result.clear();
        }

        {
            BeanInterface beanC = (BeanInterface) context.lookup("SubBeanCLocal");
            beanC.simpleMethod();
            assertEquals(expectedResult, result);
            result.clear();
        }

        {
            BeanInterface beanD = (BeanInterface) context.lookup("SubBeanDLocal");
            beanD.simpleMethod();
            assertEquals(expectedResult, result);
            result.clear();
        }

        {
            BeanInterface beanE = (BeanInterface) context.lookup("SubBeanELocal");
            beanE.simpleMethod();
            assertEquals(Arrays.asList(Call.INTERCEPTOR_AFTER_BEGIN, Call.BEAN_AFTER_BEGIN, Call.INTERCEPTOR_BEFORE_COMPLETION, Call.INTERCEPTOR_AFTER_COMPLETION), result);
            result.clear();
        }

        {
            BeanInterface beanF = (BeanInterface) context.lookup("SubBeanFLocal");
            beanF.simpleMethod();
            assertEquals(Arrays.asList(Call.INTERCEPTOR_AFTER_BEGIN, Call.INTERCEPTOR_BEFORE_COMPLETION, Call.INTERCEPTOR_AFTER_COMPLETION, Call.BEAN_AFTER_COMPLETION), result);
            result.clear();
        }

        {
            BeanInterface beanG = (BeanInterface) context.lookup("SubBeanGLocal");
            beanG.simpleMethod();
            assertEquals(Arrays.asList(Call.INTERCEPTOR_AFTER_BEGIN, Call.INTERCEPTOR_BEFORE_COMPLETION, Call.BEAN_BEFORE_COMPLETION, Call.INTERCEPTOR_AFTER_COMPLETION), result);
            result.clear();
        }
    }

    public static interface BeanInterface {

        public void simpleMethod();
    }

    public static class BaseBean implements BeanInterface {

        @TransactionAttribute(TransactionAttributeType.REQUIRED)
        public void simpleMethod() {

        }

    }

    @Stateful
    @Local(BeanInterface.class)
    public static class SubBeanA extends BaseBean implements SessionSynchronization {

        @Override
        public void afterBegin() throws EJBException, RemoteException {
            result.add(Call.BEAN_AFTER_BEGIN);
        }

        @Override
        public void afterCompletion(boolean arg0) throws EJBException, RemoteException {
            result.add(Call.BEAN_AFTER_COMPLETION);
        }

        @Override
        public void beforeCompletion() throws EJBException, RemoteException {
            result.add(Call.BEAN_BEFORE_COMPLETION);
        }
    }

    @Stateful
    @Local(BeanInterface.class)
    public static class SubBeanB extends BaseBean {

        public void afterBegin() throws EJBException, RemoteException {
            result.add(Call.BEAN_AFTER_BEGIN);
        }

        public void afterCompletion(boolean arg0) throws EJBException, RemoteException {
            result.add(Call.BEAN_AFTER_COMPLETION);
        }

        public void beforeCompletion() throws EJBException, RemoteException {
            result.add(Call.BEAN_BEFORE_COMPLETION);
        }

    }

    @Stateful
    @Local(BeanInterface.class)
    public static class SubBeanC extends BaseBean {

        @AfterBegin
        private void afterBegin() throws EJBException, RemoteException {
            result.add(Call.BEAN_AFTER_BEGIN);
        }

        @AfterCompletion
        protected void afterCompletion(boolean arg0) throws EJBException, RemoteException {
            result.add(Call.BEAN_AFTER_COMPLETION);
        }

        @BeforeCompletion
        public void beforeCompletion() throws EJBException, RemoteException {
            result.add(Call.BEAN_BEFORE_COMPLETION);
        }

    }

    @Stateful
    @Local(BeanInterface.class)
    public static class SubBeanD extends BaseBean {

        @AfterBegin
        public void afterBegin() throws EJBException, RemoteException {
            result.add(Call.BAD_VALUE);
        }

        @AfterCompletion
        public void afterCompletion(boolean arg0) throws EJBException, RemoteException {
            result.add(Call.BAD_VALUE);
        }

        @BeforeCompletion
        public void beforeCompletion() throws EJBException, RemoteException {
            result.add(Call.BAD_VALUE);
        }

        private void afterBeginNew() throws EJBException, RemoteException {
            result.add(Call.BEAN_AFTER_BEGIN);
        }

        protected void afterCompletionNew(boolean arg0) throws EJBException, RemoteException {
            result.add(Call.BEAN_AFTER_COMPLETION);
        }

        public void beforeCompletionNew() throws EJBException, RemoteException {
            result.add(Call.BEAN_BEFORE_COMPLETION);
        }
    }

    @Stateful
    @Local(BeanInterface.class)
    public static class SubBeanE extends BaseBean {

        @AfterBegin
        public void afterBegin() throws EJBException, RemoteException {
            result.add(Call.BEAN_AFTER_BEGIN);
        }
    }

    @Stateful
    @Local(BeanInterface.class)
    public static class SubBeanF extends BaseBean {

        @AfterCompletion
        public void afterCompletion(boolean arg0) throws EJBException, RemoteException {
            result.add(Call.BEAN_AFTER_COMPLETION);
        }
    }

    @Stateful
    @Local(BeanInterface.class)
    public static class SubBeanG extends BaseBean {

        @BeforeCompletion
        public void beforeCompletion() throws EJBException, RemoteException {
            result.add(Call.BEAN_BEFORE_COMPLETION);
        }
    }

    public static class SimpleInterceptor {

        @AfterBegin
        public void afterBegin(InvocationContext invocationContext) throws Exception {
            result.add(Call.INTERCEPTOR_AFTER_BEGIN);
            invocationContext.proceed();
        }

        @BeforeCompletion
        public void beforeComplete(InvocationContext invocationContext) throws Exception {
            result.add(Call.INTERCEPTOR_BEFORE_COMPLETION);
            invocationContext.proceed();
        }

        @AfterCompletion
        public void afterComplete(InvocationContext invocationContext) throws Exception {
            result.add(Call.INTERCEPTOR_AFTER_COMPLETION);
            invocationContext.proceed();
        }
    }

    public static enum Call {
        BEAN_AFTER_BEGIN, BEAN_BEFORE_COMPLETION, BEAN_AFTER_COMPLETION, BAD_VALUE, INTERCEPTOR_AFTER_BEGIN, INTERCEPTOR_BEFORE_COMPLETION, INTERCEPTOR_AFTER_COMPLETION
    }
}
