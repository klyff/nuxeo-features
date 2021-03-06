/*
 * (C) Copyright 2013 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     dmetzler
 */
package org.nuxeo.ecm.automation.io.services.contributor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import javax.ws.rs.core.HttpHeaders;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonGenerator;
import org.jboss.el.ExpressionFactoryImpl;
import org.nuxeo.common.utils.StringUtils;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.platform.actions.ActionContext;
import org.nuxeo.ecm.platform.actions.ELActionContext;
import org.nuxeo.ecm.platform.actions.ejb.ActionManager;
import org.nuxeo.ecm.platform.el.ExpressionContext;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 *
 *
 * @since 5.7.3
 */
public class RestContributorServiceImpl extends DefaultComponent implements
        RestContributorService {

    /**
    *
    */
    public static final String NXCONTENT_CATEGORY_HEADER = "X-NXContext-Category";

    protected static final Log log = LogFactory.getLog(RestContributorServiceImpl.class);

    private Map<String, ContributorDescriptor> descriptorRegistry = new ConcurrentHashMap<>();

    @Override
    public void registerContribution(Object contribution,
            String extensionPoint, ComponentInstance contributor)
            throws Exception {

        if ("contributor".equals(extensionPoint)) {
            ContributorDescriptor cd = (ContributorDescriptor) contribution;
            descriptorRegistry.put(cd.name, cd);
        }

    }

    @Override
    public void unregisterContribution(Object contribution,
            String extensionPoint, ComponentInstance contributor)
            throws Exception {
        if ("contributor".equals(extensionPoint)) {
            ContributorDescriptor cd = (ContributorDescriptor) contribution;
            if (descriptorRegistry.containsKey(cd.name)) {
                descriptorRegistry.remove(cd.name);
            }
        }
    }

    @Override
    public List<RestContributor> getContributors(String category,
            RestEvaluationContext context) {
        List<RestContributor> result = new ArrayList<>();
        for (ContributorDescriptor descriptor : getContributorDescriptors(
                category, context)) {

            RestContributor restContributor = descriptor.getRestContributor();
            result.add(restContributor);
        }

        return result;
    }

    private List<ContributorDescriptor> getContributorDescriptors(
            String category, RestEvaluationContext context) {
        List<ContributorDescriptor> result = new ArrayList<>();
        for (Entry<String, ContributorDescriptor> entry : descriptorRegistry.entrySet()) {
            ContributorDescriptor descriptor = entry.getValue();
            if (descriptor.categories.contains(category)) {
                result.add(descriptor);
            }
        }
        return result;
    }

    @Override
    public void writeContext(JsonGenerator jg, RestEvaluationContext ec)
            throws JsonGenerationException, IOException, ClientException {

        for (String category : getCategoriesToActivate(ec)) {
            for (ContributorDescriptor descriptor : getContributorDescriptors(
                    category, ec)) {
                if (evaluateFilter(ec, descriptor)) {
                    jg.writeFieldName(descriptor.name);
                    descriptor.getRestContributor().contribute(jg, ec);
                }
            }
        }

    }

    /**
     * @param ec
     * @param descriptor
     * @return
     *
     */
    private boolean evaluateFilter(RestEvaluationContext ec,
            ContributorDescriptor descriptor) {
        for (String filterId : descriptor.filterIds) {
            ActionManager as = Framework.getLocalService(ActionManager.class);
            if (!as.checkFilter(filterId, createActionContext(ec))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Creates an ActionService compatible ActionContext to evaluate filters
     *
     * @param ec
     * @return
     *
     */
    private ActionContext createActionContext(RestEvaluationContext ec) {
        ActionContext actionContext = new ELActionContext(
                new ExpressionContext(), new ExpressionFactoryImpl());
        CoreSession session = ec.getDocumentModel().getCoreSession();
        actionContext.setDocumentManager(session);
        actionContext.setCurrentPrincipal((NuxeoPrincipal) session.getPrincipal());

        actionContext.setCurrentDocument(ec.getDocumentModel());

        return actionContext;
    }

    private List<String> getCategoriesToActivate(RestEvaluationContext ec) {
        HttpHeaders headers = ec.getHeaders();
        if (headers != null) {
            List<String> requestHeader = headers.getRequestHeader(NXCONTENT_CATEGORY_HEADER);
            if (requestHeader != null && !requestHeader.isEmpty()) {
                return Arrays.asList(StringUtils.split(requestHeader.get(0),
                        ',', true));
            } else {
                return new ArrayList<>(0);
            }
        }
        return new ArrayList<>(0);
    }

}
