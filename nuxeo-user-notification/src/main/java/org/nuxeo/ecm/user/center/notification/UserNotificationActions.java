package org.nuxeo.ecm.user.center.notification;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.Factory;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoGroup;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.platform.ec.notification.UserSubscription;
import org.nuxeo.ecm.platform.ec.notification.service.NotificationService;
import org.nuxeo.ecm.platform.ec.notification.service.NotificationServiceHelper;
import org.nuxeo.ecm.platform.ec.placeful.Annotation;
import org.nuxeo.ecm.platform.ec.placeful.interfaces.PlacefulService;
import org.nuxeo.ecm.platform.notification.api.NotificationManager;

@Name("userNotificationActions")
@Scope(ScopeType.CONVERSATION)
public class UserNotificationActions implements Serializable {

    private static final long serialVersionUID = 1L;

    @In(create = true)
    protected NuxeoPrincipal currentUser;

    @In(create = true)
    protected CoreSession documentManager;

    @In(create = true)
    protected transient NotificationManager notificationManager;

    private List<UserSubscription> subscriptions;

    @Factory(value = "userSubscriptions", scope = ScopeType.EVENT)
    public List<UserSubscription> getUserSubscriptions() throws ClientException {
        PlacefulService service = NotificationServiceHelper.getPlacefulService();
        String className = service.getAnnotationRegistry().get(NotificationService.SUBSCRIPTION_NAME);
        String shortClassName = className.substring(className.lastIndexOf('.') + 1);

        PlacefulService serviceBean = NotificationServiceHelper.getPlacefulServiceBean();
        List<Annotation> tempSubscriptions = new ArrayList<Annotation>();

        // Would be much better if we write a method similar to
        // PlacefulService#getAnnotationListByParamMap
        // to run only one query using OR operand between principals

        // First, get user subscriptions
        Map<String, Object> paramMap = new HashMap<String, Object>();
        paramMap.put("userId", NuxeoPrincipal.PREFIX + currentUser.getName());

        tempSubscriptions.addAll(serviceBean.getAnnotationListByParamMap(paramMap, shortClassName));

        // Then, get group subscriptions
        for (String group : currentUser.getAllGroups()) {
            paramMap.put("userId", NuxeoGroup.PREFIX + group);
            tempSubscriptions.addAll(serviceBean.getAnnotationListByParamMap(paramMap, shortClassName));
        }
        reorderSubscriptions(tempSubscriptions);

        return subscriptions;
    }

    private void reorderSubscriptions(List<Annotation> allSubscriptions) throws ClientException {
        Map<String, List<UserSubscription>> unsortedSubscriptions = new HashMap<String, List<UserSubscription>>();
        for (Object obj : allSubscriptions) {
            UserSubscription us = (UserSubscription) obj;
            DocumentModel doc = getDocument(us.getDocId());
            String path;
            if (doc == null) {
                path = us.getDocId();
            } else {
                path = getDocument(us.getDocId()).getPathAsString();
            }
            if (!unsortedSubscriptions.containsKey(path)) {
                unsortedSubscriptions.put(path, new ArrayList<UserSubscription>());
            }
            unsortedSubscriptions.get(path).add(us);
        }
        SortedSet<String> sortedset = new TreeSet<String>(unsortedSubscriptions.keySet());
        subscriptions = new ArrayList<UserSubscription>();
        Iterator<String> it = sortedset.iterator();
        while (it.hasNext()) {
            subscriptions.addAll(unsortedSubscriptions.get(it.next()));
        }
    }

    public DocumentModel getDocument(String docId) throws ClientException {
        // test if user has READ right
        DocumentRef ref = new IdRef(docId);
        if (documentManager.exists(ref)) {
            return documentManager.getDocument(ref);
        }
        return null;
    }

    public boolean getCanRemoveNotification(String userId) {
        // Do not allow removing for group subscription
        if (userId != null && userId.equals(NuxeoPrincipal.PREFIX + currentUser.getName())) {
            return true;
        }
        return false;
    }

}
