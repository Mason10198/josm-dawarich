package org.openstreetmap.josm.plugins.dawarich;

import java.net.Authenticator.RequestorType;
import java.net.PasswordAuthentication;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.openstreetmap.josm.io.auth.*;
import org.openstreetmap.josm.spi.preferences.IPreferences;

/** Explicit opt-in credential storage, isolated from real server/OSM credentials. */
public final class SavedApiKeys {
 private final CredentialsAgent agent;
 private final IPreferences preferences;
 public SavedApiKeys(CredentialsAgent agent,IPreferences preferences){this.agent=Objects.requireNonNull(agent);this.preferences=Objects.requireNonNull(preferences);}
 static String scope(String endpoint,String sourceId){
  String normalized=ConnectionSettings.parse(endpoint,endpoint.startsWith("http://")).baseUrl();
  try{String hex=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((normalized+"\0"+sourceId).getBytes(StandardCharsets.UTF_8)));return "dawarich-"+hex.substring(0,32)+"."+hex.substring(32)+".invalid";}catch(NoSuchAlgorithmException e){throw new IllegalStateException("Credential saving is unavailable");}
 }
 private String sensitive(String endpoint,String id){
  String host=scope(endpoint,id);CredentialsAgent marker=agent;
  // addSensitive checks the *backend class*, not the CredentialsManager facade.
  if(agent instanceof CredentialsManager manager&&manager.getCredentialsAgentClass()==JosmPreferencesCredentialAgent.class)marker=new JosmPreferencesCredentialAgent();
  preferences.addSensitive(marker,"server.password."+host);preferences.addSensitive(marker,"server.username."+host);return host;
 }
 String load(String endpoint,String id){
  try{PasswordAuthentication value=agent.lookup(RequestorType.SERVER,sensitive(endpoint,id));if(value==null)return null;char[] chars=value.getPassword();try{return chars.length==0?null:new String(chars);}finally{Arrays.fill(chars,'\0');}}
  catch(CredentialsAgentException|RuntimeException e){throw new IllegalStateException("Could not read the saved API key; check JOSM's credential store");}
 }
 void save(String endpoint,String id,String key){
  char[] chars=key==null?new char[0]:key.toCharArray();
  try{String host=sensitive(endpoint,id);agent.store(RequestorType.SERVER,host,new PasswordAuthentication("dawarich",chars));agent.purgeCredentialsCache(RequestorType.SERVER,host);}
  catch(CredentialsAgentException|RuntimeException e){throw new IllegalStateException("Could not save or forget the API key; check JOSM's credential store");}
  finally{Arrays.fill(chars,'\0');}
 }
 void delete(String endpoint,String id){save(endpoint,id,null);}
}
