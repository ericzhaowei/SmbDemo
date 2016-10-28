package com.ider.smbtest;

import android.content.Context;

import java.lang.reflect.Method;

/**
 * Created by ider-eric on 2016/8/10.
 */
public class PropertyReader {



    public static String getString(String key) {

        String ret= "";

        try{

            @SuppressWarnings("rawtypes")
            Class SystemProperties = Class.forName("android.os.SystemProperties");

            //参数类型
            @SuppressWarnings("rawtypes")
            Class[] paramTypes= new Class[1];
            paramTypes[0]= String.class;

            Method get = SystemProperties.getMethod("get", paramTypes);

            //参数
            Object[] params= new Object[1];
            params[0]= new String(key);

            ret= (String) get.invoke(SystemProperties, params);

        }catch( IllegalArgumentException iAE ){
            throw iAE;
        }catch( Exception e ){
            ret= "";
        }

        return ret;

    }

    public static void set(String key, String value) {
        try {
            Class SystemProperties = Class.forName("android.os.SystemProperties");
            Class[] paramTypes = new Class[2];
            paramTypes[0] = String.class;
            paramTypes[1] = String.class;

            Method set = SystemProperties.getMethod("set", paramTypes);

            // 参数
            Object[] params = new Object[2];
            params[0] = new String(key);
            params[1] = new String(value);

            set.invoke(SystemProperties, params);


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
