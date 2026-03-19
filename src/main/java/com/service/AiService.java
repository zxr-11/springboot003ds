package com.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.entity.BaoxiuxinxiEntity;
import com.entity.FeiyongxinxiEntity;
import com.entity.YezhuxinxiEntity;
import com.entity.YonghuEntity;

@Service
public class AiService {
	@Value("${ai.baseUrl:}")
	private String baseUrl;

	@Value("${ai.apiKey:}")
	private String apiKey;

	@Value("${ai.model:gpt-4o-mini}")
	private String model;

	public String chat(String message) {
		return chat(null, null, null, message, null);
	}

	@javax.annotation.Resource
	private YezhuxinxiService yezhuxinxiService;
	@javax.annotation.Resource
	private YonghuService yonghuService;
	@javax.annotation.Resource
	private BaoxiuxinxiService baoxiuxinxiService;
	@javax.annotation.Resource
	private FeiyongxinxiService feiyongxinxiService;

	public String chat(String role, String tableName, String username, String message, List<Map<String, Object>> history) {
		String effectiveRole = normalizeRole(role);
		if (StringUtils.isBlank(effectiveRole)) {
			return "请先登录";
		}

		// 先尝试“系统内查询/统计”（只读），命中就直接返回
		String direct = tryDirectAnswer(effectiveRole, tableName, username, message);
		if (direct != null) {
			return direct;
		}

		// 对于“明显是业务数据查询”的问题，禁止调用外部大模型，避免胡编
		if (isBusinessDataQuestion(message)) {
			return localReply(effectiveRole, message);
		}

		// 默认：不配置 key 时走“本地规则”回答，保证开箱即用
		if (StringUtils.isBlank(apiKey) || StringUtils.isBlank(baseUrl)) {
			return localReply(effectiveRole, message);
		}
		try {
			return openAiCompatibleChat(effectiveRole, message, history);
		} catch (Exception e) {
			// 外部调用失败时，降级到本地规则回答，避免前端直接报错
			return localReply(effectiveRole, message);
		}
	}

	private String tryDirectAnswer(String role, String tableName, String username, String message) {
		if (StringUtils.isBlank(message)) {
			return null;
		}
		String m = message.trim();
		String lower = m.toLowerCase();

		// 仅在已登录（能拿到 tableName/username）时执行查询
		if (StringUtils.isBlank(tableName) || StringUtils.isBlank(username)) {
			return null;
		}

		String r = normalizeRole(role);

		// 1) 我的车牌号（用户）
		if ((lower.contains("车牌") || lower.contains("车牌号")) && (lower.contains("我") || lower.contains("我的"))) {
			if (!"yonghu".equals(tableName)) return "抱歉，你只能查看自己的信息";
			YonghuEntity y = yonghuService.selectOne(new EntityWrapper<YonghuEntity>().eq("gerenzhanghao", username));
			if (y == null || StringUtils.isBlank(y.getChepaihao())) return "未查到";
			return y.getChepaihao();
		}

		// 1.1) 管理员按姓名/账号查询用户车牌号（例如：韩美车牌号 / 查询韩美车牌 / 用户1车牌号）
		if ((lower.contains("车牌") || lower.contains("车牌号")) && ("users".equals(tableName) || "管理员".equals(role))) {
			// 尝试提取姓名（简单做法：去掉“车牌/车牌号/是多少/查询/一下/的”等）
			String name = m;
			name = name.replace("车牌号", "").replace("车牌", "");
			name = name.replace("是多少", "").replace("多少", "").replace("查询", "").replace("查", "").replace("一下", "").replace("的", "");
			name = name.trim();
			if (StringUtils.isBlank(name)) {
				return null;
			}
			// 先按姓名精确，再按姓名模糊，再按账号精确
			YonghuEntity y = yonghuService.selectOne(new EntityWrapper<YonghuEntity>().eq("xingming", name));
			if (y == null) {
				y = yonghuService.selectOne(new EntityWrapper<YonghuEntity>().like("xingming", name));
			}
			if (y == null) {
				y = yonghuService.selectOne(new EntityWrapper<YonghuEntity>().eq("gerenzhanghao", name));
			}
			if (y == null) {
				return "未查到";
			}
			JSONObject out = new JSONObject();
			out.put("姓名", y.getXingming());
			out.put("个人账号", y.getGerenzhanghao());
			out.put("车牌号", y.getChepaihao());
			return out.toJSONString();
		}

		// 2) 业主信息数量（管理员：全部；用户：只看自己的；员工：无）
		if (lower.contains("业主") && (lower.contains("多少") || lower.contains("几条") || lower.contains("数量") || lower.contains("多少条"))) {
			if ("yuangong".equals(tableName)) return "抱歉，暂无权限";
			EntityWrapper<YezhuxinxiEntity> ew = new EntityWrapper<>();
			if ("yonghu".equals(tableName)) {
				ew.eq("gerenzhanghao", username);
			}
			int count = yezhuxinxiService.selectCount(ew);
			return String.valueOf(count);
		}

		// 3) 我的报修数量（用户：自己的；员工/管理员：全部）
		if (lower.contains("报修") && (lower.contains("多少") || lower.contains("几条") || lower.contains("数量") || lower.contains("多少条"))) {
			EntityWrapper<BaoxiuxinxiEntity> ew = new EntityWrapper<>();
			if ("yonghu".equals(tableName)) {
				ew.eq("gerenzhanghao", username);
			} else if ("yuangong".equals(tableName)) {
				// 没有“负责人字段”，员工无法按“本人负责”精确过滤
				return "抱歉，暂无权限";
			}
			int count = baoxiuxinxiService.selectCount(ew);
			return String.valueOf(count);
		}

		// 4) 费用是否支付（用户：仅自己；管理员：可按姓名/账号/门牌号查）
		if (lower.contains("支付") || lower.contains("未支付") || lower.contains("已支付") || lower.contains("ispay")) {
			EntityWrapper<FeiyongxinxiEntity> ew = new EntityWrapper<>();
			if ("yonghu".equals(tableName)) {
				ew.eq("gerenzhanghao", username);
			} else if ("yuangong".equals(tableName)) {
				return "抱歉，暂无权限";
			} else {
				// 管理员：尝试从句子里提取查询条件（姓名/账号/门牌号）
				String q = m;
				q = q.replace("是否支付", "").replace("是不是支付", "").replace("有没有支付", "");
				q = q.replace("是否已支付", "").replace("是否未支付", "");
				q = q.replace("支付了吗", "").replace("支付没", "").replace("支付没?", "").replace("支付没？", "");
				q = q.replace("已支付", "").replace("未支付", "").replace("支付", "");
				q = q.replace("查询", "").replace("查", "").replace("一下", "").replace("的", "");
				q = q.trim();

				// 先按门牌号（常见：1单元101 / 101 / A-101 等；这里用 like 兜底）
				if (StringUtils.isNotBlank(q)) {
					FeiyongxinxiEntity byMenpai = feiyongxinxiService
							.selectOne(new EntityWrapper<FeiyongxinxiEntity>().like("menpaihao", q));
					if (byMenpai != null) {
						JSONObject out = new JSONObject();
						out.put("姓名", byMenpai.getXingming());
						out.put("门牌号", byMenpai.getMenpaihao());
						out.put("楼房名称", byMenpai.getLoufangmingcheng());
						out.put("是否支付", byMenpai.getIspay());
						return out.toJSONString();
					}
				}

				// 再按姓名 / 账号
				if (StringUtils.isNotBlank(q)) {
					ew.andNew().eq("xingming", q).or().eq("gerenzhanghao", q);
				} else {
					return "请提供姓名/个人账号/门牌号";
				}
			}

			FeiyongxinxiEntity fee = feiyongxinxiService.selectOne(ew);
			if (fee == null) {
				return "未查到";
			}
			JSONObject out = new JSONObject();
			out.put("姓名", fee.getXingming());
			out.put("门牌号", fee.getMenpaihao());
			out.put("楼房名称", fee.getLoufangmingcheng());
			out.put("是否支付", fee.getIspay());
			return out.toJSONString();
		}

		return null;
	}

	private boolean isBusinessDataQuestion(String message) {
		if (StringUtils.isBlank(message)) {
			return false;
		}
		String m = message.trim();
		String lower = m.toLowerCase();
		// 命中这些关键词，基本都是“查系统数据/状态”，优先走查库/本地规则，禁止外部模型胡编
		return lower.contains("车牌") || lower.contains("业主") || lower.contains("报修") || lower.contains("费用")
				|| lower.contains("支付") || lower.contains("门牌") || lower.contains("楼房") || lower.contains("公告")
				|| lower.contains("部门") || lower.contains("员工") || lower.contains("用户") || lower.contains("投诉")
				|| lower.contains("停车") || lower.contains("车位") || lower.contains("编号")
				|| lower.contains("多少") || lower.contains("几条") || lower.contains("数量")
				|| lower.contains("查询") || lower.contains("查") || lower.contains("信息");
	}

	private String normalizeRole(String role) {
		if (role == null) {
			return "";
		}
		String r = role.trim();
		if ("管理员".equals(r) || "用户".equals(r) || "员工".equals(r)) {
			return r;
		}
		return "";
	}

	private String localReply(String role, String message) {
		String m = message.trim();
		String lower = m.toLowerCase();
		String r = normalizeRole(role);
		// “在哪查/怎么查/在哪里看” -> 给出菜单指引（仅系统操作层面，不涉及代码/配置）
		if (lower.contains("在哪") || lower.contains("哪里") || lower.contains("怎么查") || lower.contains("如何查") || lower.contains("怎么看")) {
			String module = null;
			if (lower.contains("业主")) module = "业主信息";
			else if (lower.contains("费用")) module = "费用信息";
			else if (lower.contains("报修")) module = "报修信息";
			else if (lower.contains("停车")) module = "停车信息";
			else if (lower.contains("车位")) module = "车位信息";
			else if (lower.contains("投诉")) module = "投诉编号";
			else if (lower.contains("公告")) module = "公告信息";
			else if (lower.contains("楼房")) module = "楼房信息";
			else if (lower.contains("部门")) module = "部门信息";
			else if (lower.contains("员工")) module = "员工";
			else if (lower.contains("用户")) module = "用户";
			else if (lower.contains("ai")) module = "AI助手";

			if (module != null) {
				if ("管理员".equals(r)) {
					return "请在左侧菜单进入【" + module + "】列表页查询（支持按姓名/账号/门牌号/编号等筛选）。";
				}
				return "请在左侧菜单进入【" + module + "】列表页查询。";
			}
			return "你想查哪一类信息？比如：业主信息/费用信息/报修信息/停车信息/投诉编号/公告信息。";
		}
		if (lower.contains("你好") || lower.contains("hello") || lower.contains("hi")) {
			return "你好";
		}
		// 不在页面里提示“改代码/改文件/改配置”，只做系统操作指引
		if (m.length() <= 30) {
			return "请更具体一点";
		}
		return "请提供要查询的对象与条件";
	}

	/**
	 * OpenAI 兼容接口（也兼容多数第三方网关）
	 * baseUrl 例子：https://api.openai.com
	 */
	private String openAiCompatibleChat(String role, String message, List<Map<String, Object>> history) throws Exception {
		String url = baseUrl;
		if (url.endsWith("/")) {
			url = url.substring(0, url.length() - 1);
		}
		url = url + "/v1/chat/completions";

		JSONObject payload = new JSONObject();
		payload.put("model", model);
		JSONArray messages = new JSONArray();

		// 系统提示词：把 AI 限定为“只回答本项目相关”
		String roleHint = normalizeRole(role);
		JSONObject system = new JSONObject();
		system.put("role", "system");
		system.put("content",
				"你是小区物业系统AI助手。仅返回简洁的文字或JSON数据（无分析、无额外说明），语气自然人性化。"
						+ "当前身份为【" + roleHint + "】。"
						+ "查询规则："
						+ "1) 身份为【管理员】：可查询所有数据，直接返回，不提示无权限；"
						+ "2) 身份为【员工】：仅返回本人负责的报修/投诉/楼房/公告/部门数据，其他返回“抱歉，暂无权限”；"
						+ "3) 身份为【用户】：仅返回本人数据，查他人数据返回“抱歉，你只能查看自己的信息”；"
						+ "4) 必须严格按当前身份回答，不要自行推断或提升权限。"
						+ "系统菜单布局（用于告诉用户“在哪里查询”）："
						+ "【管理员】AI功能->AI助手；用户管理->用户；员工管理->员工；业主信息管理->业主信息；费用信息管理->费用信息；楼房信息管理->楼房信息；报修信息管理->报修信息；车位信息管理->车位信息；停车信息管理->停车信息；投诉编号管理->投诉编号；公告信息管理->公告信息；部门信息管理->部门信息。"
						+ "【用户】AI功能->AI助手；业主信息管理->业主信息；费用信息管理->费用信息；楼房信息管理->楼房信息；报修信息管理->报修信息；车位信息管理->车位信息；停车信息管理->停车信息；投诉编号管理->投诉编号；公告信息管理->公告信息。"
						+ "【员工】AI功能->AI助手；楼房信息管理->楼房信息；报修信息管理->报修信息；投诉编号管理->投诉编号；公告信息管理->公告信息；部门信息管理->部门信息。"
						+ "当用户问“怎么查/在哪查/在哪里看”时，优先返回：菜单路径 + 关键筛选字段（例如 姓名/个人账号/门牌号/编号/日期）。"
						+ "格式：优先自然文字，必要时附带JSON（仅数据，无多余字段）。"
						+ "禁止提及任何“修改代码/文件/接口/数据库/配置”等内容。");
		messages.add(system);

		// 追加最近对话（如果前端传了）
		if (history != null && !history.isEmpty()) {
			int start = Math.max(0, history.size() - 12);
			for (int i = start; i < history.size(); i++) {
				Map<String, Object> h = history.get(i);
				if (h == null) {
					continue;
				}
				String hRole = h.get("role") == null ? null : h.get("role").toString();
				String content = h.get("content") == null ? null : h.get("content").toString();
				if (StringUtils.isBlank(hRole) || StringUtils.isBlank(content)) {
					continue;
				}
				if (!"user".equals(hRole) && !"assistant".equals(hRole) && !"system".equals(hRole)) {
					continue;
				}
				JSONObject hm = new JSONObject();
				hm.put("role", hRole);
				hm.put("content", content);
				messages.add(hm);
			}
		}

		JSONObject user = new JSONObject();
		user.put("role", "user");
		user.put("content", message);
		messages.add(user);
		payload.put("messages", messages);

		HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
		conn.setRequestMethod("POST");
		conn.setConnectTimeout(8000);
		conn.setReadTimeout(60000);
		conn.setDoOutput(true);
		conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
		conn.setRequestProperty("Authorization", "Bearer " + apiKey);

		byte[] out = payload.toJSONString().getBytes(StandardCharsets.UTF_8);
		try (OutputStream os = conn.getOutputStream()) {
			os.write(out);
		}

		int code = conn.getResponseCode();
		BufferedReader br = new BufferedReader(new InputStreamReader(
				code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(),
				StandardCharsets.UTF_8));
		StringBuilder sb = new StringBuilder();
		String line;
		while ((line = br.readLine()) != null) {
			sb.append(line);
		}
		String raw = sb.toString();

		if (code < 200 || code >= 300) {
			throw new RuntimeException("AI 调用失败: HTTP " + code + " " + raw);
		}

		JSONObject resp = JSONObject.parseObject(raw);
		JSONArray choices = resp.getJSONArray("choices");
		if (choices == null || choices.isEmpty()) {
			return "";
		}
		JSONObject msg = choices.getJSONObject(0).getJSONObject("message");
		return msg == null ? "" : msg.getString("content");
	}
}

