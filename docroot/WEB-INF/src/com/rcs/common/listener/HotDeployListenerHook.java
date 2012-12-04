package com.rcs.common.listener;


import com.liferay.portal.kernel.deploy.hot.HotDeployEvent;
import com.liferay.portal.kernel.deploy.hot.HotDeployException;
import com.liferay.portal.kernel.deploy.hot.HotDeployListener;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.util.HttpUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.xml.Document;
import com.liferay.portal.kernel.xml.DocumentException;
import com.liferay.portal.kernel.xml.Element;
import com.liferay.portal.kernel.xml.SAXReaderUtil;
import com.liferay.portal.util.Portal;
import com.rcs.common.config.ApplicationPropsBean;
import com.rcs.common.service.LocaleService;
import com.rcs.service.model.MessageSource;
import com.rcs.service.service.MessageSourceLocalServiceUtil;
import com.rcs.service.service.persistence.MessageSourcePK;
import com.rcs.service.service.persistence.MessageSourcePersistence;
import com.rcs.service.service.persistence.MessageSourceUtil;
//import com.rcs.i18n.common.cache.CacheService;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.ServletContext;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;

public class HotDeployListenerHook implements HotDeployListener {

    private static final Logger _logger = Logger.getLogger(HotDeployListenerHook.class);

    public static final String DEFAULT_RESOURCE_NAME = "Language.properties";

    public static final String DEFAULT_RESOURCE_PREFIX = "Language_";
    public static final String DEFAULT_RESOURCE_SUFFIX = ".properties";

    public static final String LIFERAY_HOOK_PATH = "/WEB-INF/liferay-hook.xml";

    @Autowired
    private static LocaleService localeService;

    @Autowired
    private static MessageSourcePersistence messageSourcePersistence;

    @Autowired
    private static ApplicationPropsBean props;

    //private static CacheService cacheService = ObjectFactory.getBean("ehCacheService");

    //private static final ClassLoader CLASS_LOADER = messageSourcePersistence.getClass().getClassLoader();

    //private static final Locale[] availableLocales = localeService.getAvailableLocales(PortalUtil.getDefaultCompanyId());

    @Override
    public void invokeDeploy(HotDeployEvent hotDeployEvent) throws HotDeployException {

    	
        String contextName = hotDeployEvent.getServletContextName();

        if(isResourceEditor(contextName)){
            return;
        }

        ServletContext servletContext = hotDeployEvent.getServletContext();

        ClassLoader contextClassLoader = hotDeployEvent.getContextClassLoader();

        String bundleName = hotDeployEvent.getPluginPackage().getName();

        _logger.error("current bundleName: " + bundleName);
        // determines whether <resource-bundle> specified in 'portlet.xml' or <language-properties> specified in 'liferay-hook.xml'
        boolean resourceBundleSpecified = false;

        String portletXML = StringUtils.EMPTY;
        try {
            portletXML = HttpUtil.URLtoString(servletContext.getResource("/WEB-INF/" + Portal.PORTLET_XML_FILE_NAME_STANDARD));
        } catch (IOException e) {
            _logger.error("Can not read portlet.xml");
        }

        if (StringUtils.isNotBlank(portletXML)) {

            /*== try to read resource bundle from portlet.xml ===*/
            try {
                resourceBundleSpecified = readPortletXML(portletXML, contextClassLoader, bundleName);
            } catch (DocumentException e) {
                if (_logger.isDebugEnabled()) {
                    _logger.debug("Unable to read process xml. ");
                }
            }
        }

        // if <resource-bundle> not specified in portlet.xml -  read from liferay-hook.xml
        if (!resourceBundleSpecified) {

            String liferayHookXml = StringUtils.EMPTY;
            try {
                liferayHookXml = HttpUtil.URLtoString(servletContext.getResource(LIFERAY_HOOK_PATH));
            } catch (IOException e) {
                _logger.error("Can not read liferay-hook.xml");
            }

            if (StringUtils.isNotBlank(liferayHookXml)) {

                /*== try to read resource bundle from liferay-hook.xml ===*/
                try {
                    resourceBundleSpecified = readLiferayHookXML(liferayHookXml, contextClassLoader, bundleName);
                } catch (DocumentException e) {
                    if (_logger.isDebugEnabled()) {
                        _logger.debug("Unable to read process xml. ");
                    }
                }

            }

        }

        // if <resource-bundle> not specified - use default
        if (!resourceBundleSpecified) {
            processBundle(DEFAULT_RESOURCE_NAME, contextClassLoader, bundleName);
        }
    }

    /*
   *  Reads 'resource-bundle' property from liferay-hook.xml
   * */
    private boolean readLiferayHookXML(String xml, ClassLoader classLoader, String bundleName) throws DocumentException {

        boolean resourceBundleSpecified = false;

        Document document = SAXReaderUtil.read(xml, true);

        Element hookElement = document.getRootElement();

        if (hookElement != null) {

            String resourceBundleName = hookElement.elementText("language-properties");

            if (resourceBundleName != null) {

                resourceBundleSpecified = true;

                processBundle(resourceBundleName, classLoader, bundleName);
            }

        }

        return resourceBundleSpecified;
    }

    /*
    *  Reads 'resource-bundle' property from portlet.xml
    * */
    private boolean readPortletXML(String xml, ClassLoader classLoader, String bundleName) throws DocumentException {

        boolean resourceBundleSpecified = false;

        Document document = SAXReaderUtil.read(xml, true);

        Element rootElement = document.getRootElement();

        for (Element portletElement : rootElement.elements("portlet")) {

            String resourceBundleName = portletElement.elementText("resource-bundle").replace('.', '/');

            if (resourceBundleName != null) {

                resourceBundleSpecified = true;

                processBundle(resourceBundleName, classLoader, bundleName);
            }
        }

        return resourceBundleSpecified;
    }


    /*
    *   Processes resource bundle for specified locale
    * */
    private void processBundle(String resourceBundleName, ClassLoader classLoader, String bundleName) {

        String baseResourceName = resourceBundleName;

        if (resourceBundleName.endsWith(DEFAULT_RESOURCE_SUFFIX))
            baseResourceName = StringUtils.substringBefore(resourceBundleName, DEFAULT_RESOURCE_SUFFIX);

        URL defaultResource = classLoader.getResource(resourceBundleName);

        if (defaultResource != null) {

            Properties bundle = new Properties();
            try {
                InputStream inStream = defaultResource.openStream();
                bundle.load(inStream);
                inStream.close();
            } catch (IOException e) {
                _logger.warn("Could not read file", e);
            }

            processBundle(Locale.getDefault(), bundle, bundleName);
        }
        //@@ added!
        Locale[] availableLocales = LanguageUtil.getAvailableLocales();
        for (Locale locale : availableLocales) {

            String resourceName = baseResourceName + StringPool.UNDERLINE + locale.getLanguage() + DEFAULT_RESOURCE_SUFFIX;

            URL resource = classLoader.getResource(resourceName);

            if (resource == null) {
                resourceName = baseResourceName + StringPool.UNDERLINE + locale.toString() + DEFAULT_RESOURCE_SUFFIX;
                resource = classLoader.getResource(resourceName);
            }

            if (resource != null) {

                Properties bundle = new Properties();
                try {
                    InputStream inStream = resource.openStream();
                    bundle.load(inStream);
                    inStream.close();
                } catch (IOException e) {
                    _logger.warn("Could not read file", e);
                    continue;
                }

                processBundle(locale, bundle, bundleName);
            }
        }
    }

    /*
    *   Processes resource bundle for specified locale
    * */
    private void  processBundle(Locale locale, Properties bundle, String bundleName) {

        for (Map.Entry property : bundle.entrySet()) {

            String key = (String) property.getKey();
            String value = (String) property.getValue();
            
            try {
            
	            //MessageSourcePK messageSourcePK = new MessageSourcePK(key, locale.toString()); 
	            //MessageSource messageSource = MessageSourceUtil.findByResourceKeyAndLocale(key, locale.toString());
            	MessageSourcePK messageSourcePK = new MessageSourcePK(key, locale.toString());
	        	MessageSource messageSource = MessageSourceLocalServiceUtil.fetchMessageSource(messageSourcePK);
            	
	            if (messageSource == null) {
	
	            	//long msId = CounterLocalServiceUtil.increment(MessageSource.class.getName());	            	
	            	messageSource = MessageSourceUtil.create(messageSourcePK);
	            	//messageSource.setResourceKey(key);
	            	//messageSource.setResourceLocale(locale.toString());
	                messageSource.setValue(value);                
	                messageSource.setBundle(bundleName);
	                MessageSourceLocalServiceUtil.updateMessageSource(messageSource);
	                /*
	                ClassLoader currentClassLoader = Thread.currentThread().getContextClassLoader();
	                try {
	                    Thread.currentThread().setContextClassLoader(CLASS_LOADER);
	                    messageSourcePersistence.insert(messageSource);
	                } finally {
	                    Thread.currentThread().setContextClassLoader(currentClassLoader);
	                }
					@@
	                //put into cache
	                if (props.isCacheEnabled()) {
	                    cacheService.putResult(MessageSource.class.getSimpleName(), null,
	                            new Object[]{key, locale}, messageSource);
	                }*/
	            }
            } catch (SystemException ex) {
            	ex.printStackTrace();
            } catch (Exception ex) {
            	ex.printStackTrace();
            }
        }

    }

    private boolean isResourceEditor(String contextName){
        return contextName.equals("resource-editor-hook");
    }

    @Override
    public void invokeUndeploy(HotDeployEvent event) throws HotDeployException {

    }
}
