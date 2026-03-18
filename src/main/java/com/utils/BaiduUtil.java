package com.utils;

import java.util.HashMap;
import java.util.Map;

import com.alibaba.fastjson.JSONObject;


/**
* 类说明 : 
*/

public class BaiduUtil {
	
    /**
     * 根据经纬度获得省市区信息
     * @param lon 纬度
     * @param lat 经度
     * @param coordtype 经纬度坐标系
     * @return
     */
    public static Map<String, String> getCityByLonLat(String key, String lng, String lat) {
        String location = lat + "," + lng;
        try {
            //拼装url
            String url = "http://api.map.baidu.com/reverse_geocoding/v3/?ak="+key+"&output=json&coordtype=wgs84ll&location="+location;
            String result = HttpClientUtils.doGet(url);
            JSONObject o = JSONObject.parseObject(result);
            Map<String, String> area = new HashMap<>();
			area.put("province", o.getJSONObject("result").getJSONObject("addressComponent").getString("province"));
			area.put("city", o.getJSONObject("result").getJSONObject("addressComponent").getString("city"));
			area.put("district", o.getJSONObject("result").getJSONObject("addressComponent").getString("district"));
			area.put("street", o.getJSONObject("result").getJSONObject("addressComponent").getString("street"));
            return area;
        }catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
