package com.transiva.app;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URI;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/** Small, dependency-free TransShop image loader. No network work on the UI thread. */
final class TransShopImages {
 private static final String HOST="https://transiva.my.id/";
 private static final ExecutorService WORK=Executors.newFixedThreadPool(2);
 private static final Handler MAIN=new Handler(Looper.getMainLooper());
 private static final Map<String,Bitmap> CACHE=new LinkedHashMap<String,Bitmap>(32,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,Bitmap> e){return size()>32;}};
 private TransShopImages(){}
 static String field(JSONObject o, String... names){if(o==null)return "";for(String n:names){String v=o.optString(n,"").trim();if(!v.isEmpty()&&!v.equalsIgnoreCase("null"))return v;}return "";}
 static String shop(JSONObject o){return field(o,"logo_url","photo_url","image_url","profile_image_url","profile_photo_url","store_photo_url","shop_photo_url","logo","photo","image","foto_profil","foto_toko");}
 static String product(JSONObject o){return field(o,"image_url","photo_url","product_image_url","product_photo_url","image","photo","foto","foto_produk");}
 static String url(String raw){try{if(raw==null||raw.trim().isEmpty())return "";raw=raw.trim().replace("\\","/");if(raw.startsWith("//"))raw="https:"+raw;String full;if(raw.startsWith("https://"))full=raw;else if(raw.startsWith("http://"))return "";else if(raw.startsWith("/"))full=HOST+raw.substring(1);else if(raw.startsWith("server/"))full=HOST+raw;else full=HOST+"server/"+raw;URI u=new URI(full).normalize();if(!"https".equalsIgnoreCase(u.getScheme())||!"transiva.my.id".equalsIgnoreCase(u.getHost()))return "";return u.toASCIIString();}catch(Exception e){return "";}}
 static void load(ImageView view,String raw){String address=url(raw);view.setBackgroundColor(Color.rgb(238,245,255));view.setScaleType(ImageView.ScaleType.CENTER_CROP);view.setTag(address);if(address.isEmpty())return;Bitmap cached; synchronized(CACHE){cached=CACHE.get(address);}if(cached!=null){view.setImageBitmap(cached);return;}WORK.execute(()->{HttpURLConnection c=null;Bitmap bitmap=null;try{c=(HttpURLConnection)new URL(address).openConnection();c.setConnectTimeout(6000);c.setReadTimeout(8000);c.setInstanceFollowRedirects(false);if(c.getResponseCode()!=200)return;String mime=c.getContentType();if(mime==null||!mime.toLowerCase().startsWith("image/"))return;if(c.getContentLengthLong()>4*1024*1024)return;try(InputStream stream=c.getInputStream()){BitmapFactory.Options opt=new BitmapFactory.Options();opt.inSampleSize=2;bitmap=BitmapFactory.decodeStream(stream,null,opt);}if(bitmap!=null){synchronized(CACHE){CACHE.put(address,bitmap);}}}catch(Exception ignored){}finally{if(c!=null)c.disconnect();}final Bitmap result=bitmap;MAIN.post(()->{if(result!=null&&address.equals(view.getTag()))view.setImageBitmap(result);});});}
}
