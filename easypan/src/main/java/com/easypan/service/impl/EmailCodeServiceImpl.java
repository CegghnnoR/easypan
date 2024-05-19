package com.easypan.service.impl;

import com.easypan.entity.config.AppConfig;
import com.easypan.entity.constants.Constants;
import com.easypan.entity.enums.PageSize;
import com.easypan.entity.po.EmailCode;
import com.easypan.entity.po.UserInfo;
import com.easypan.entity.query.EmailCodeQuery;
import com.easypan.entity.query.SimplePage;
import com.easypan.entity.query.UserInfoQuery;
import com.easypan.entity.vo.PaginationResultVO;
import com.easypan.exception.BusinessException;
import com.easypan.mappers.EmailCodeMapper;
import com.easypan.mappers.UserInfoMapper;
import com.easypan.service.EmailCodeService;
import com.easypan.utils.StringTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.mail.internet.MimeMessage;
import java.util.Date;
import java.util.List;
import java.util.Objects;


/**
 * 邮箱验证码 业务接口实现
 */
@Service("emailCodeService")
public class EmailCodeServiceImpl implements EmailCodeService {
	private static final Logger logger = LoggerFactory.getLogger(EmailCodeServiceImpl.class);

	@Resource
	private EmailCodeMapper<EmailCode, EmailCodeQuery> emailCodeMapper;
	@Resource
	private UserInfoMapper<UserInfo, UserInfoQuery> userInfoMapper;
	@Resource
	private JavaMailSender javaMailSender;
	@Resource
	private AppConfig appConfig;
	/**
	 * 根据条件查询列表
	 */
	@Override
	public List<EmailCode> findListByParam(EmailCodeQuery param) {
		return this.emailCodeMapper.selectList(param);
	}

	/**
	 * 根据条件查询列表
	 */
	@Override
	public Integer findCountByParam(EmailCodeQuery param) {
		return this.emailCodeMapper.selectCount(param);
	}

	/**
	 * 分页查询方法
	 */
	@Override
	public PaginationResultVO<EmailCode> findListByPage(EmailCodeQuery param) {
		int count = this.findCountByParam(param);
		int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

		SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
		param.setSimplePage(page);
		List<EmailCode> list = this.findListByParam(param);
		PaginationResultVO<EmailCode> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
		return result;
	}

	/**
	 * 新增
	 */
	@Override
	public Integer add(EmailCode bean) {
		return this.emailCodeMapper.insert(bean);
	}

	/**
	 * 批量新增
	 */
	@Override
	public Integer addBatch(List<EmailCode> listBean) {
		if (listBean == null || listBean.isEmpty()) {
			return 0;
		}
		return this.emailCodeMapper.insertBatch(listBean);
	}

	/**
	 * 批量新增或者修改
	 */
	@Override
	public Integer addOrUpdateBatch(List<EmailCode> listBean) {
		if (listBean == null || listBean.isEmpty()) {
			return 0;
		}
		return this.emailCodeMapper.insertOrUpdateBatch(listBean);
	}

	/**
	 * 多条件更新
	 */
	@Override
	public Integer updateByParam(EmailCode bean, EmailCodeQuery param) {
		StringTools.checkParam(param);
		return this.emailCodeMapper.updateByParam(bean, param);
	}

	/**
	 * 多条件删除
	 */
	@Override
	public Integer deleteByParam(EmailCodeQuery param) {
		StringTools.checkParam(param);
		return this.emailCodeMapper.deleteByParam(param);
	}

	/**
	 * 根据EmailAndCode获取对象
	 */
	@Override
	public EmailCode getEmailCodeByEmailAndCode(String email, String code) {
		return this.emailCodeMapper.selectByEmailAndCode(email, code);
	}

	/**
	 * 根据EmailAndCode修改
	 */
	@Override
	public Integer updateEmailCodeByEmailAndCode(EmailCode bean, String email, String code) {
		return this.emailCodeMapper.updateByEmailAndCode(bean, email, code);
	}

	/**
	 * 根据EmailAndCode删除
	 */
	@Override
	public Integer deleteEmailCodeByEmailAndCode(String email, String code) {
		return this.emailCodeMapper.deleteByEmailAndCode(email, code);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void sendEmailCode(String email, Integer type) {
		if (Objects.equals(type, Constants.ZERO)) {
			UserInfo userInfo = userInfoMapper.selectByEmail(email);
			if (userInfo != null) {
				throw new BusinessException("邮箱已存在");
			}
		}
		String code = StringTools.getRandomNumber(Constants.LENGTH_5);
		// TODO 发送验证码

		// 将之前的验证码置为无效
		emailCodeMapper.disableEmailCode(email);

		EmailCode emailCode = new EmailCode();
		emailCode.setCode(code);
		emailCode.setEmail(email);
		emailCode.setStatus(Constants.ZERO);
		emailCode.setCreateTime(new Date());
		emailCodeMapper.insert(emailCode);
	}

	private void sendMailCode(String toEmail, String code) {
		try {
			MimeMessage message = javaMailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true);
			helper.setFrom(appConfig.getSendUserName());
		} catch (Exception e) {
			logger.error("邮件发送失败");
			throw new BusinessException("邮件发送失败");
		}

	}
}