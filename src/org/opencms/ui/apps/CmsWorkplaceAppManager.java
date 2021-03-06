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

package org.opencms.ui.apps;

import org.opencms.db.CmsUserSettings;
import org.opencms.file.CmsObject;
import org.opencms.file.CmsUser;
import org.opencms.main.CmsException;
import org.opencms.main.CmsLog;
import org.opencms.main.OpenCms;
import org.opencms.ui.apps.scheduler.CmsScheduledJobsAppConfig;
import org.opencms.util.CmsStringUtil;
import org.opencms.workplace.tools.CmsTool;
import org.opencms.workplace.tools.CmsToolManager;
import org.opencms.workplace.tools.I_CmsToolHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import org.apache.commons.logging.Log;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * The workplace app manager.<p>
 */
public class CmsWorkplaceAppManager {

    /**
     * Wrapper for the navigation state.<p>
     */
    public static class NavigationState {

        /** The parameter separator. */
        public static final String PARAM_SEPARATOR = "/";

        /** The state parameters. */
        private String m_params = "";

        /** The view/app name. */
        private String m_viewName = "";

        /**
         * Constructor.<p>
         *
         * @param stateString the state string to parse
         */
        public NavigationState(String stateString) {

            if (stateString.startsWith("!")) {
                stateString = stateString.substring(1);
            }
            int separatorPos = stateString.indexOf(PARAM_SEPARATOR);
            if (separatorPos > 0) {
                m_viewName = stateString.substring(0, separatorPos);
                m_params = stateString.substring(separatorPos + 1);
            } else {
                m_viewName = stateString;
            }
            if (m_viewName.endsWith("/")) {
                m_viewName = m_viewName.substring(0, m_viewName.length() - 1);
            }
        }

        /**
         * Returns the parameter part of the state.<p>
         *
         * @return the parameters
         */
        String getParams() {

            return m_params;
        }

        /**
         * Returns the view name.<p>
         *
         * @return the view name
         */
        String getViewName() {

            return m_viewName;

        }
    }

    /** The workplace app settings additional info key. */
    public static String WORKPLACE_APP_SETTINGS_KEY = "WORKPLACE_APP_SETTINGS";

    /** The logger for this class. */
    protected static Log LOG = CmsLog.getLog(CmsWorkplaceAppManager.class.getName());

    /** Legacy apps explicitly hidden from new workplace. */
    private static final Set<String> LEGACY_BLACKLIST = Sets.newConcurrentHashSet(Arrays.asList("/git", "/scheduler"));

    /** The app categories. */
    private List<CmsAppCategory> m_appCategories = Lists.newArrayList();

    /** The configured apps. */
    private Map<String, I_CmsWorkplaceAppConfiguration> m_appsById = Maps.newHashMap();

    /**
     * Returns the app configuration with the given id.<p>
     *
     * @param appId the app id
     *
     * @return the app configuration
     */
    public I_CmsWorkplaceAppConfiguration getAppConfiguration(String appId) {

        return m_appsById.get(appId);
    }

    /**
     * Returns the user app setting of the given type.<p>
     *
     * @param cms the cms context
     * @param type the app setting type
     *
     * @return the app setting
     *
     * @throws InstantiationException in case instantiating the settings type fails
     * @throws IllegalAccessException in case the settings default constructor is not accessible
     */
    public <T extends I_CmsAppSettings> T getAppSettings(CmsObject cms, Class<T> type)
    throws InstantiationException, IllegalAccessException {

        CmsUser user = cms.getRequestContext().getCurrentUser();
        CmsUserSettings settings = new CmsUserSettings(user);
        String settingsString = settings.getAdditionalPreference(type.getName(), true);
        T result = type.newInstance();

        if (CmsStringUtil.isNotEmptyOrWhitespaceOnly(settingsString)) {

            result.restoreSettings(settingsString);

        }

        return result;
    }

    /**
     * Returns the configured categories.<p>
     *
     * @return the app categories
     */
    public List<CmsAppCategory> getCategories() {

        return Collections.unmodifiableList(m_appCategories);
    }

    /**
     * Returns all available workplace apps.<p>
     *
     * @return the available workpllace apps
     */
    public Collection<I_CmsWorkplaceAppConfiguration> getWorkplaceApps() {

        return m_appsById.values();
    }

    /**
     * Loads the workplace apps.<p>
     */
    public void loadApps() {

        m_appsById.clear();
        if (m_appCategories == null) {
            m_appCategories = Lists.newArrayList();
        }
        m_appCategories.clear();
        CmsAppCategory c1 = new CmsAppCategory("Main", null, 0, 0, null);
        m_appCategories.addAll(Arrays.asList(c1, new CmsAppCategory("Legacy", null, 1, 0, null)));
        addAppConfigurations(loadDefaultApps());
        addAppConfigurations(loadAppsUsingServiceLoader());
        addAppConfigurations(loadLegacyApps());
    }

    /**
     * Stores the given app setting within the users additional info.<p>
     *
     * @param cms the cms context
     * @param type the app setting type, used as the settings key
     * @param appSettings the settings to store
     */
    public void storeAppSettings(CmsObject cms, Class<? extends I_CmsAppSettings> type, I_CmsAppSettings appSettings) {

        CmsUser user = cms.getRequestContext().getCurrentUser();
        CmsUserSettings settings = new CmsUserSettings(user);

        String currentSetting = settings.getAdditionalPreference(type.getName(), true);
        String state = appSettings.getSettingsString();
        if (((state == null) && (currentSetting == null)) || ((state != null) && state.equals(currentSetting))) {
            // nothing changed
            return;
        }

        settings.setAdditionalPreference(type.getName(), state);
        try {
            settings.save(cms);
        } catch (CmsException e) {
            LOG.error("Failed to store workplace app settings for type " + type.getName(), e);
        }
    }

    /**
     * Adds the given app configuration.<p>
     *
     * @param appConfigs the app configuration
     */
    private void addAppConfigurations(Collection<I_CmsWorkplaceAppConfiguration> appConfigs) {

        for (I_CmsWorkplaceAppConfiguration appConfig : appConfigs) {
            I_CmsWorkplaceAppConfiguration old = m_appsById.get(appConfig.getId());
            if ((old == null) || (old.getPriority() < appConfig.getPriority())) {
                m_appsById.put(appConfig.getId(), appConfig);
            }
        }
    }

    /**
     * Returns the configured apps using the service loader.<p>
     *
     * @return tthe configured apps
     */
    private List<I_CmsWorkplaceAppConfiguration> loadAppsUsingServiceLoader() {

        List<I_CmsWorkplaceAppConfiguration> appConfigurations = new ArrayList<I_CmsWorkplaceAppConfiguration>();
        Iterator<I_CmsWorkplaceAppConfiguration> configs = ServiceLoader.load(
            I_CmsWorkplaceAppConfiguration.class).iterator();
        while (configs.hasNext()) {
            appConfigurations.add(configs.next());
        }
        return appConfigurations;
    }

    /**
     * Loads the default apps.<p>
     *
     * @return the default apps
     */
    private Collection<I_CmsWorkplaceAppConfiguration> loadDefaultApps() {

        List<I_CmsWorkplaceAppConfiguration> result = Lists.newArrayList();
        result.addAll(
            Arrays.<I_CmsWorkplaceAppConfiguration> asList(
                new CmsSitemapEditorConfiguration(),
                new CmsPageEditorConfiguration(),
                new CmsFileExplorerConfiguration(),
                new CmsScheduledJobsAppConfig(),
                new CmsAppHierarchyConfiguration()));
        return result;
    }

    /**
     * Loads the legacy apps.<p>
     *
     * @return the legacy apps
     */
    private Collection<I_CmsWorkplaceAppConfiguration> loadLegacyApps() {

        List<I_CmsWorkplaceAppConfiguration> configs = new ArrayList<I_CmsWorkplaceAppConfiguration>();

        List<CmsTool> tools = OpenCms.getWorkplaceManager().getToolManager().getToolHandlers();
        for (CmsTool tool : tools) {

            I_CmsToolHandler handler = tool.getHandler();
            String path = handler.getPath();

            // only collecting first path level tools
            if ((path.length() > 1) && (path.indexOf(CmsToolManager.TOOLPATH_SEPARATOR, 1) < 0)) {
                if (!LEGACY_BLACKLIST.contains(path)) {
                    configs.add(new CmsLegacyAppConfiguration(handler));
                }
            }

        }
        return configs;
    }

}
