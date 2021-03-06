/*
 * This library is part of OpenCms -
 * the Open Source Content Management System
 *
 * Copyright (c) Alkacon Software GmbH (http://www.alkacon.com)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * For further information about Alkacon Software, please see the
 * company website: http://www.alkacon.com
 *
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.opencms.ui.dialogs.availability;

import org.opencms.file.CmsObject;
import org.opencms.file.CmsProperty;
import org.opencms.file.CmsPropertyDefinition;
import org.opencms.file.CmsResource;
import org.opencms.file.CmsResourceFilter;
import org.opencms.gwt.CmsVfsService;
import org.opencms.gwt.shared.CmsAvailabilityInfoBean;
import org.opencms.gwt.shared.CmsPrincipalBean;
import org.opencms.loader.CmsLoaderException;
import org.opencms.lock.CmsLockActionRecord;
import org.opencms.lock.CmsLockActionRecord.LockChange;
import org.opencms.lock.CmsLockException;
import org.opencms.lock.CmsLockUtil;
import org.opencms.main.CmsException;
import org.opencms.main.CmsLog;
import org.opencms.main.OpenCms;
import org.opencms.security.CmsAccessControlEntry;
import org.opencms.ui.A_CmsUI;
import org.opencms.ui.CmsVaadinUtils;
import org.opencms.ui.I_CmsDialogContext;
import org.opencms.ui.components.CmsBasicDialog;
import org.opencms.ui.components.CmsResourceInfo;
import org.opencms.util.CmsStringUtil;
import org.opencms.workplace.CmsWorkplace;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;

import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.data.Validator;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.CheckBox;
import com.vaadin.ui.DateField;
import com.vaadin.ui.Panel;
import com.vaadin.ui.TextField;
import com.vaadin.ui.VerticalLayout;

/**
 * Availability dialog.<p>
 */
public class CmsAvailabilityDialog extends CmsBasicDialog {

    /** Logger for this class. */
    private static final Log LOG = CmsLog.getLog(CmsAvailabilityDialog.class);

    /** Serial version id. */
    private static final long serialVersionUID = 1L;

    /** Availability info. */
    private CmsAvailabilityInfoBean m_availabilityInfo;

    /** The cancel button. */
    private Button m_cancelButton;

    /** The dialog context. */
    private I_CmsDialogContext m_dialogContext;

    /** Date field. */
    private DateField m_expiredField;

    /** Initial value for 'notification enabled. */
    private Boolean m_initialNotificationEnabled = Boolean.FALSE;

    /** Initial value for notification interval. */
    private String m_initialNotificationInterval = "";

    /** 'Modify siblings' check box. */
    private CheckBox m_modifySiblingsField;

    /**  'enable notification' check box. */
    private CheckBox m_notificationEnabledField;

    /** Field for the notification interval. */
    private TextField m_notificationIntervalField;

    /** Panel for notifications. */
    private Panel m_notificationPanel;

    /** OK button. */
    private Button m_okButton;

    /** Date field. */
    private DateField m_releasedField;

    /** Option to reset the expiration. */
    private CheckBox m_resetExpired;

    /** Option to reset the relase date. */
    private CheckBox m_resetReleased;

    /** Container for responsibles widgets. */
    private VerticalLayout m_responsiblesContainer;

    /** Panel for 'Responsibles'. */
    private Panel m_responsiblesPanel;

    /** Option to enable subresource modification. */
    private CheckBox m_subresourceModificationField;

    /**
     * Creates a new instance.<p>
     *
     * @param dialogContext the dialog context
     */
    public CmsAvailabilityDialog(I_CmsDialogContext dialogContext) {

        super();
        m_dialogContext = dialogContext;
        CmsObject cms = dialogContext.getCms();
        CmsVaadinUtils.readAndLocalizeDesign(
            this,
            OpenCms.getWorkplaceManager().getMessages(A_CmsUI.get().getLocale()),
            null);
        List<CmsResource> resources = dialogContext.getResources();
        m_notificationIntervalField.addValidator(new Validator() {

            /** Serial version id. */
            private static final long serialVersionUID = 1L;

            public void validate(Object value) throws InvalidValueException {

                String strValue = ((String)value).trim();
                if (!strValue.matches("[0-9]*")) {
                    throw new InvalidValueException(
                        CmsVaadinUtils.getMessageText(org.opencms.ui.Messages.GUI_VALIDATOR_EMPTY_OR_NUMBER_0));

                }

            }
        });

        boolean hasSiblings = false;
        for (CmsResource resource : m_dialogContext.getResources()) {
            hasSiblings |= resource.getSiblingCount() > 1;
            if (hasSiblings) {
                break;
            }
        }
        m_modifySiblingsField.setVisible(hasSiblings);

        if (resources.size() == 1) {
            CmsResource onlyResource = resources.get(0);
            if (onlyResource.getDateReleased() != CmsResource.DATE_RELEASED_DEFAULT) {
                m_releasedField.setValue(new Date(onlyResource.getDateReleased()));
            }
            if (onlyResource.getDateExpired() != CmsResource.DATE_EXPIRED_DEFAULT) {
                m_expiredField.setValue(new Date(onlyResource.getDateExpired()));
            }
            initNotification();
            Map<CmsPrincipalBean, String> responsibles = m_availabilityInfo.getResponsibles();
            if (!responsibles.isEmpty()) {
                m_responsiblesPanel.setVisible(true);
                m_notificationPanel.setVisible(true);
                for (Map.Entry<CmsPrincipalBean, String> entry : responsibles.entrySet()) {
                    CmsPrincipalBean principal = entry.getKey();
                    String icon = principal.isGroup()
                    ? CmsWorkplace.getResourceUri("buttons/group.png")
                    : CmsWorkplace.getResourceUri("buttons/user.png");
                    String subtitle = "";
                    if (entry.getValue() != null) {
                        subtitle = cms.getRequestContext().removeSiteRoot(entry.getValue());
                    }
                    CmsResourceInfo infoWidget = new CmsResourceInfo(entry.getKey().getName(), subtitle, icon);
                    m_responsiblesContainer.addComponent(infoWidget);

                }
            }
        } else {
            boolean showNotification = false;
            resourceLoop: for (CmsResource resource : resources) {
                try {
                    List<CmsAccessControlEntry> aces = cms.getAccessControlEntries(cms.getSitePath(resource));
                    for (CmsAccessControlEntry ace : aces) {
                        if (ace.isResponsible()) {
                            showNotification = true;
                            break resourceLoop;
                        }
                    }
                } catch (CmsException e) {
                    LOG.error(e.getLocalizedMessage(), e);
                }
            }
            m_notificationPanel.setVisible(showNotification);
        }
        m_notificationEnabledField.setValue(m_initialNotificationEnabled);
        m_notificationIntervalField.setValue(m_initialNotificationInterval);
        boolean hasFolders = false;
        for (CmsResource resource : resources) {
            if (resource.isFolder()) {
                hasFolders = true;
            }
        }
        m_subresourceModificationField.setVisible(hasFolders);
        initResetCheckbox(m_resetReleased, m_releasedField);
        initResetCheckbox(m_resetExpired, m_expiredField);
        m_okButton.addClickListener(new ClickListener() {

            private static final long serialVersionUID = 1L;

            @SuppressWarnings("synthetic-access")
            public void buttonClick(ClickEvent event) {

                if (validate()) {
                    try {

                        changeAvailability();
                        m_dialogContext.finish(null);
                    } catch (Throwable t) {
                        m_dialogContext.error(t);
                    }
                }
            }

        });

        m_cancelButton.addClickListener(new ClickListener() {

            private static final long serialVersionUID = 1L;

            @SuppressWarnings("synthetic-access")
            public void buttonClick(ClickEvent event) {

                m_dialogContext.finish(null);
            }
        });
        displayResourceInfo(m_dialogContext.getResources());
    }

    /**
     * Initializes the values for the notification widgets.<p>
     */
    public void initNotification() {

        if (m_dialogContext.getResources().size() == 1) {
            CmsResource resource = m_dialogContext.getResources().get(0);
            try {
                m_availabilityInfo = CmsVfsService.getAvailabilityInfoStatic(A_CmsUI.getCmsObject(), resource);
                m_initialNotificationInterval = "" + m_availabilityInfo.getNotificationInterval();
                m_initialNotificationEnabled = Boolean.valueOf(m_availabilityInfo.isNotificationEnabled());
            } catch (CmsLoaderException e) {
                LOG.error(e.getLocalizedMessage(), e);
            } catch (CmsException e) {
                LOG.error(e.getLocalizedMessage(), e);
            }

        }
    }

    /**
     * Actually performs the availability change.<p>
     *
     * @throws CmsException if something goes wrong
     */
    protected void changeAvailability() throws CmsException {

        Date released = m_releasedField.getValue();
        Date expired = m_expiredField.getValue();
        boolean resetReleased = m_resetReleased.getValue().booleanValue();
        boolean resetExpired = m_resetExpired.getValue().booleanValue();
        boolean modifySubresources = m_subresourceModificationField.getValue().booleanValue();
        for (CmsResource resource : m_dialogContext.getResources()) {
            changeAvailability(resource, released, resetReleased, expired, resetExpired, modifySubresources);
        }

        String notificationInterval = m_notificationIntervalField.getValue().trim();
        int notificationIntervalInt = 0;
        try {
            notificationIntervalInt = Integer.parseInt(notificationInterval);
        } catch (NumberFormatException e) {
            LOG.warn(e.getLocalizedMessage(), e);
        }
        Boolean notificationEnabled = m_notificationEnabledField.getValue();
        boolean notificationSettingsUnchanged = notificationInterval.equals(m_initialNotificationInterval)
            && notificationEnabled.equals(m_initialNotificationEnabled);

        CmsObject cms = A_CmsUI.getCmsObject();
        if (!notificationSettingsUnchanged) {
            for (CmsResource resource : m_dialogContext.getResources()) {
                performSingleResourceNotification(
                    A_CmsUI.getCmsObject(),
                    cms.getSitePath(resource),
                    notificationEnabled.booleanValue(),
                    notificationIntervalInt,
                    m_modifySiblingsField.getValue().booleanValue());
            }

        }

    }

    /**
     * Changes availability.<p>
     *
     * @param resource the resource
     * @param released release date
     * @param resetReleased reset release date
     * @param expired expiration date
     * @param resetExpired reset expiration date
     * @param modifySubresources modify children
     *
     * @throws CmsException if something goes wrong
     */
    private void changeAvailability(
        CmsResource resource,
        Date released,
        boolean resetReleased,
        Date expired,
        boolean resetExpired,
        boolean modifySubresources) throws CmsException {

        CmsObject cms = m_dialogContext.getCms();
        CmsLockActionRecord lockActionRecord = CmsLockUtil.ensureLock(cms, resource);
        try {
            long newDateReleased;
            if (resetReleased || (released != null)) {
                newDateReleased = released != null ? released.getTime() : CmsResource.DATE_RELEASED_DEFAULT;
                cms.setDateReleased(resource, newDateReleased, modifySubresources);
            }
            long newDateExpired;
            if (resetExpired || (expired != null)) {
                newDateExpired = expired != null ? expired.getTime() : CmsResource.DATE_EXPIRED_DEFAULT;
                cms.setDateExpired(resource, newDateExpired, modifySubresources);
            }
        } finally {
            if (lockActionRecord.getChange() == LockChange.locked) {
                try {
                    cms.unlockResource(resource);
                } catch (CmsLockException e) {
                    LOG.warn(e.getLocalizedMessage(), e);
                }
            }

        }
    }

    /**
     * Creates a reset checkbox which can enable / disable a date field.<p>
     *
     * @param box the check box
     * @param field the date field
     */
    private void initResetCheckbox(CheckBox box, final DateField field) {

        box.addValueChangeListener(new ValueChangeListener() {

            private static final long serialVersionUID = 1L;

            public void valueChange(ValueChangeEvent event) {

                Boolean value = (Boolean)(event.getProperty().getValue());
                if (value.booleanValue()) {
                    field.clear();
                    field.setEnabled(false);
                } else {
                    field.setEnabled(true);
                }
            }
        });
    }

    /**
     * Performs the notification operations on a single resource.<p>
     *
     * @param cms the CMS context
     * @param resName the VFS path of the resource
     * @param enableNotification if the notification is activated
     * @param notificationInterval the notification interval in days
     * @param modifySiblings flag indicating to include resource siblings
     *
     * @throws CmsException if the availability and notification operations fail
     */
    private void performSingleResourceNotification(
        CmsObject cms,
        String resName,
        boolean enableNotification,
        int notificationInterval,
        boolean modifySiblings) throws CmsException {

        List<CmsResource> resources = new ArrayList<CmsResource>();
        if (modifySiblings) {
            // modify all siblings of a resource
            resources = cms.readSiblings(resName, CmsResourceFilter.IGNORE_EXPIRATION);
        } else {
            // modify only resource without siblings
            resources.add(cms.readResource(resName, CmsResourceFilter.IGNORE_EXPIRATION));
        }
        Iterator<CmsResource> i = resources.iterator();
        while (i.hasNext()) {
            CmsResource resource = i.next();
            String resourcePath = cms.getRequestContext().removeSiteRoot(resource.getRootPath());
            // lock resource if auto lock is enabled
            CmsLockActionRecord lockRecord = CmsLockUtil.ensureLock(cms, resource);
            try {
                // write notification settings
                writeProperty(
                    cms,
                    resourcePath,
                    CmsPropertyDefinition.PROPERTY_NOTIFICATION_INTERVAL,
                    String.valueOf(notificationInterval));
                writeProperty(
                    cms,
                    resourcePath,
                    CmsPropertyDefinition.PROPERTY_ENABLE_NOTIFICATION,
                    String.valueOf(enableNotification));
            } finally {
                if (lockRecord.getChange() == LockChange.locked) {
                    cms.unlockResource(resource);
                }
            }
        }
    }

    /**
     * Validates release / expiration.<p>
     *
     * @return true if the fields are valid.
     */
    private boolean validate() {

        return m_releasedField.isValid() && m_expiredField.isValid();
    }

    /**
     * Writes a property value for a resource.<p>
     *
     * @param cms the cms context
     * @param resourcePath the path of the resource
     * @param propertyName the name of the property
     * @param propertyValue the new value of the property
     *
     * @throws CmsException if something goes wrong
     */
    private void writeProperty(CmsObject cms, String resourcePath, String propertyName, String propertyValue)
    throws CmsException {

        if (CmsStringUtil.isEmpty(propertyValue)) {
            propertyValue = CmsProperty.DELETE_VALUE;
        }

        CmsProperty newProp = new CmsProperty();
        newProp.setName(propertyName);
        CmsProperty oldProp = cms.readPropertyObject(resourcePath, propertyName, false);
        if (oldProp.isNullProperty()) {
            // property value was not already set
            if (OpenCms.getWorkplaceManager().isDefaultPropertiesOnStructure()) {
                newProp.setStructureValue(propertyValue);
            } else {
                newProp.setResourceValue(propertyValue);
            }
        } else {
            if (oldProp.getStructureValue() != null) {
                newProp.setStructureValue(propertyValue);
                newProp.setResourceValue(oldProp.getResourceValue());
            } else {
                newProp.setResourceValue(propertyValue);
            }
        }

        newProp.setAutoCreatePropertyDefinition(true);

        String oldStructureValue = oldProp.getStructureValue();
        String newStructureValue = newProp.getStructureValue();
        if (CmsStringUtil.isEmpty(oldStructureValue)) {
            oldStructureValue = CmsProperty.DELETE_VALUE;
        }
        if (CmsStringUtil.isEmpty(newStructureValue)) {
            newStructureValue = CmsProperty.DELETE_VALUE;
        }

        String oldResourceValue = oldProp.getResourceValue();
        String newResourceValue = newProp.getResourceValue();
        if (CmsStringUtil.isEmpty(oldResourceValue)) {
            oldResourceValue = CmsProperty.DELETE_VALUE;
        }
        if (CmsStringUtil.isEmpty(newResourceValue)) {
            newResourceValue = CmsProperty.DELETE_VALUE;
        }

        // change property only if it has been changed
        if (!oldResourceValue.equals(newResourceValue) || !oldStructureValue.equals(newStructureValue)) {
            cms.writePropertyObject(resourcePath, newProp);
        }
    }
}
