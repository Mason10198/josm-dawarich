package org.openstreetmap.josm.plugins.dawarich;
import java.net.*;
public final class ConnectionSettings {
 private final URI base;
 private ConnectionSettings(URI base){this.base=base;}
 public static ConnectionSettings parse(String value, boolean allowHttp){
  try { URI u=new URI(value); String scheme=u.getScheme();
   if(u.isOpaque()||u.getHost()==null||u.getRawUserInfo()!=null||u.getRawQuery()!=null||u.getRawFragment()!=null||!("https".equalsIgnoreCase(scheme)||("http".equalsIgnoreCase(scheme)&&allowHttp))) throw new IllegalArgumentException("Invalid connection URL");
   String path=u.getRawPath()==null?"":u.getRawPath(); while(path.endsWith("/")) path=path.substring(0,path.length()-1);
   return new ConnectionSettings(new URI(scheme.toLowerCase(),null,u.getHost(),u.getPort(),path,null,null));
  } catch(URISyntaxException|NullPointerException e){throw new IllegalArgumentException("Invalid connection URL");}
 }
 public URI pointsUri(){try{return new URI(base.getScheme(),null,base.getHost(),base.getPort(),base.getRawPath()+"/api/v1/points",null,null);}catch(URISyntaxException e){throw new IllegalStateException(e);}}
 public String baseUrl(){return base.toString();}
}
