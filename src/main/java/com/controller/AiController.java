package com.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import javax.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.service.AiService;
import com.utils.R;

@RequestMapping("ai")
@RestController
public class AiController {
	@Autowired
	private AiService aiService;

	/**
	 * 简单 AI 对话接口：
	 * - 默认走本地规则回答（无需任何 key）
	 * - 如果配置了 ai.apiKey / ai.baseUrl，可转为调用大模型
	 */
	@PostMapping("/chat")
	public R chat(@RequestBody Map<String, Object> body, HttpServletRequest request) {
		String message = body == null ? null : (body.get("message") == null ? null : body.get("message").toString());
		if (StringUtils.isBlank(message)) {
			return R.error(400, "message 不能为空");
		}
		String role = request.getSession().getAttribute("role") == null ? null : request.getSession().getAttribute("role").toString();
		String username = request.getSession().getAttribute("username") == null ? null : request.getSession().getAttribute("username").toString();
		String tableName = request.getSession().getAttribute("tableName") == null ? null : request.getSession().getAttribute("tableName").toString();

		List<Map<String, Object>> messages = null;
		if (body != null && body.get("messages") instanceof List) {
			@SuppressWarnings("unchecked")
			List<Object> raw = (List<Object>) body.get("messages");
			messages = new ArrayList<>();
			for (Object o : raw) {
				if (o instanceof Map) {
					@SuppressWarnings("unchecked")
					Map<String, Object> m = (Map<String, Object>) o;
					messages.add(m);
				}
			}
		}

		String reply = aiService.chat(role, tableName, username, message, messages);
		return R.ok().put("data", reply);
	}
}

