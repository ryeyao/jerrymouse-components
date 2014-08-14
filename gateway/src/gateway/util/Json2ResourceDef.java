package gateway.util;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import wshare.dc.resource.ResourceDefinition;

import java.io.*;
import java.lang.reflect.Field;

/**
 * Created with IntelliJ IDEA.
 * User: Rye
 * Date: 3/27/14
 * Time: 4:52 PM
 */
public class Json2ResourceDef {

    public static ResourceDefinition parse(String jsonString) {
        JsonParser jp = new JsonParser();
        JsonElement jo = jp.parse(jsonString);

        Gson g = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
        ResourceDefinition def = g.fromJson(jo, ResourceDefinition.class);

        return def;
    }

    public static ResourceDefinition parse(Reader reader) {
        JsonParser jp = new JsonParser();
//        JsonElement je = jp.parse(reader);
//        FieldNamingStrategy fns = new FieldNamingStrategy() {
//            @Override
//            public String translateName(Field f) {
//                System.out.println("Field: " + f.getName());
//                if (f.getName() == "RES_2_USER") {
//                    return "RESOURCE_2_USER";
//                } else if (f.getName() == "USER_2_RES") {
//                    return "USER_2_RESOURCE";
//                }
//                return f.getName();
//            }
//        };
//        Gson g = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").setFieldNamingStrategy(fns).create();
        Gson g = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
        ResourceDefinition def = g.fromJson(reader, ResourceDefinition.class);
//        System.out.println(def.properties);
        return def;
    }

    public static ResourceDefinition parse(JsonReader jreader) {
        Gson g = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
        jreader.setLenient(true);
        return g.fromJson(jreader, ResourceDefinition.class);
    }

    public static void main(String[] args) throws IOException {
        File dir = new File("F:\\repositories\\wshare-2.0-gateway-1.0\\out\\artifacts\\Jerrymouse\\components\\gateway-camera\\resources\\Component.cam_1.json");
        BufferedReader br = new BufferedReader(new FileReader(dir));

        String line = br.readLine();
        String res = "";
        while(line != null) {
            res+=line;
            line = br.readLine();
        }

        System.out.println("res:\n" + res);
        System.out.println("line: " + line);
//        File dir = new File("sensor/resources");
        for(File file : dir.listFiles()) {
            ResourceDefinition def = Json2ResourceDef.parse(new FileReader(file));
        }

    }

}
