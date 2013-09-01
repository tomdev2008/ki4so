package com.github.ebnew.ki4so.core.authentication;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.github.ebnew.ki4so.core.app.App;
import com.github.ebnew.ki4so.core.app.AppService;
import com.github.ebnew.ki4so.core.exception.NoKi4soKeyException;
import com.github.ebnew.ki4so.core.key.KeyService;
import com.github.ebnew.ki4so.core.key.Ki4soKey;
import com.github.ebnew.ki4so.core.model.EncryCredentialInfo;

/**
 * 默认的认证后处理器实现类，提供抽象方法由具体子类实现。
 * @author burgess yang
 *
 */
public class DefaultAuthenticationPostHandler implements
		AuthenticationPostHandler {
	
	private static Logger logger = Logger.getLogger(DefaultAuthenticationPostHandler.class.getName());
	
	/**
	 * 密钥持续过期时间，3个月。
	 */
	private static final long DURATION = 3*30*24*60*60*1000;
	
	private EncryCredentialManager encryCredentialManager;
	
	private KeyService keyService;
	
	private AppService appService;

	public AppService getAppService() {
		return appService;
	}

	public void setAppService(AppService appService) {
		this.appService = appService;
	}

	public KeyService getKeyService() {
		return keyService;
	}

	public void setKeyService(KeyService keyService) {
		this.keyService = keyService;
	}

	public EncryCredentialManager getEncryCredentialManager() {
		return encryCredentialManager;
	}

	public void setEncryCredentialManager(
			EncryCredentialManager encryCredentialManager) {
		this.encryCredentialManager = encryCredentialManager;
	}

	@Override
	public Authentication postAuthentication(Credential credential, Principal principal){
		Date createTime = new Date();
		//若认证通过，则返回认证结果对象。
		AuthenticationImpl authentication = new AuthenticationImpl();
		authentication.setAuthenticatedDate(createTime);
		authentication.setPrincipal(principal);
		encryCredentialWithKi4soKey(authentication, credential, principal);
		encryCredentialWithAppKey(authentication, credential, principal);
		return authentication;
	}
	
	/*
	 * 使用ki4so服务器本身的key对凭据信息进行加密处理。
	 */
	private void encryCredentialWithKi4soKey(AuthenticationImpl authentication, Credential credential, Principal principal){
		//如果是原始凭据，则需要进行加密处理。
		if(credential.isOriginal()){
			//查找ki4so服务对应的应用信息。
			App ki4soApp = appService.findKi4soServerApp();
			if(ki4soApp==null){
				logger.log(Level.SEVERE, "no ki4so key info.");
				throw NoKi4soKeyException.INSTANCE; 
			}
			String encryCredential = encryCredentialManager.encrypt(buildEncryCredentialInfo(ki4soApp.getAppId(), authentication, principal));
			//加密后的凭据信息写入到动态属性中。
			Map<String, Object> attributes = authentication.getAttributes();
			if(attributes==null){
				attributes = new HashMap<String, Object>();
			}
			attributes.put(KI4SO_SERVER_EC_KEY, encryCredential);
			authentication.setAttributes(attributes);
		}
	}
	
	/*
	 * 使用ki4so服务器本身的key对凭据信息进行加密处理。
	 */
	private void encryCredentialWithAppKey(AuthenticationImpl authentication, Credential credential, Principal principal){
		//获得登录的应用信息。
		AbstractParameter abstractParameter = null;
		if(credential instanceof AbstractParameter){
			abstractParameter = (AbstractParameter)credential;
		}
		//若登录对应的服务参数service的值不为空，则使用该service对应的应用的key进行加密。
		if(abstractParameter!=null && abstractParameter.getParameterValue("service")!=null){
			String service = abstractParameter.getParameterValue("service").toString();
			//service不为空，且符合Http协议URL格式，则继续加密。
			if(service.length()>0 && service.startsWith("http://")){
				//查找ki4so服务对应的应用信息。
				App clientApp = appService.findAppByHost(service);
				if(clientApp!=null){
					String encryCredential = encryCredentialManager.encrypt(buildEncryCredentialInfo(clientApp.getAppId(), authentication, principal));
					//加密后的凭据信息写入到动态属性中。
					Map<String, Object> attributes = authentication.getAttributes();
					if(attributes==null){
						attributes = new HashMap<String, Object>();
					}
					attributes.put(KI4SO_CLIENT_EC_KEY, encryCredential);
					attributes.put("service", service);
					authentication.setAttributes(attributes);
				}
			}
		}
	}
	
	private EncryCredentialInfo buildEncryCredentialInfo(String appId, AuthenticationImpl authentication, Principal principal){
		EncryCredentialInfo encryCredentialInfo = new EncryCredentialInfo();
		Ki4soKey ki4soKey = keyService.findKeyByAppId(appId);
		encryCredentialInfo.setAppId(appId);
		encryCredentialInfo.setCreateTime(authentication.getAuthenticatedDate());
		encryCredentialInfo.setUserId(principal.getId());
		encryCredentialInfo.setKeyId(ki4soKey.getKeyId());
		Date expiredTime = new Date((authentication.getAuthenticatedDate().getTime()+DURATION)); 
		encryCredentialInfo.setExpiredTime(expiredTime);
		return encryCredentialInfo;
	}

}
