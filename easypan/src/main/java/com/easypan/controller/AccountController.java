package com.easypan.controller;

import com.easypan.annotation.GlobalInterceptor;
import com.easypan.annotation.VerifyParam;
import com.easypan.component.RedisComponent;
import com.easypan.entity.config.AppConfig;
import com.easypan.entity.constants.Constants;
import com.easypan.entity.dto.CreateImageCode;
import com.easypan.entity.dto.SessionWebUserDto;
import com.easypan.entity.dto.UserSpaceDto;
import com.easypan.entity.enums.VerifyRegexEnum;
import com.easypan.entity.po.UserInfo;
import com.easypan.entity.vo.ResponseVO;
import com.easypan.exception.BusinessException;
import com.easypan.service.EmailCodeService;
import com.easypan.service.UserInfoService;
import com.easypan.utils.StringTools;
import io.netty.util.Constant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * 用户信息 Controller
 */
@RestController("userInfoController")
public class AccountController extends ABaseController{

	private static final String CONTENT_TYPE = "Content-Type";
	private static final String CONTENT_TYPE_VALUE = "application/json;charset=UTF-8";
	private static final Logger logger = LoggerFactory.getLogger(AccountController.class);
	@Resource
	private UserInfoService userInfoService;
	@Resource
	private EmailCodeService emailCodeService;
	@Resource
	private AppConfig appConfig;
	@Resource
	private RedisComponent redisComponent;
	@RequestMapping("/checkCode")
	public void checkCode(HttpServletResponse response, HttpSession session, Integer type) throws IOException {
		CreateImageCode vCode = new CreateImageCode(130, 38, 5, 10);
		response.setHeader("Pragma", "no-cache");
		response.setHeader("Cache-Control", "no-cache");
		response.setDateHeader("Expires", 0);
		response.setContentType("image/jpeg");
		String code = vCode.getCode();
		if (type == null || type == 0) {
			session.setAttribute(Constants.CHECK_CODE_KEY, code);
		} else {
			session.setAttribute(Constants.CHECK_CODE_KEY_EMAIL, code);
		}
		vCode.write(response.getOutputStream());
	}

	@RequestMapping("/sendEmailCode")
	@GlobalInterceptor(checkParams = true, checkLogin = false)
	public ResponseVO sendEmailCode(HttpSession session,
									@VerifyParam(required = true, regex = VerifyRegexEnum.EMAIL, max = 150) String email,
									@VerifyParam(required = true) String checkCode,
									@VerifyParam(required = true) Integer type) {
		try {
			if (!checkCode.equalsIgnoreCase((String) session.getAttribute(Constants.CHECK_CODE_KEY_EMAIL))) {
				throw new BusinessException("图片验证码不正确");
			}
			emailCodeService.sendEmailCode(email, type);
			return getSuccessResponseVO(null);
		} finally {
			session.removeAttribute(Constants.CHECK_CODE_KEY_EMAIL);
		}
	}
	@RequestMapping("/register")
	@GlobalInterceptor(checkParams = true, checkLogin = false)
	public ResponseVO register(HttpSession session,
							   @VerifyParam(required = true, regex = VerifyRegexEnum.EMAIL, max = 150) String email,
							   @VerifyParam(required = true) String nickName,
							   @VerifyParam(required = true, regex = VerifyRegexEnum.PASSWORD) String password,
							   @VerifyParam(required = true) String checkCode,
							   @VerifyParam(required = true) String emailCode) {
		try {
			if (!checkCode.equalsIgnoreCase((String) session.getAttribute(Constants.CHECK_CODE_KEY))) {
				throw new BusinessException("图片验证码不正确");
			}
			userInfoService.register(email, nickName, password, emailCode);
			return getSuccessResponseVO(null);
		} finally {
			session.removeAttribute(Constants.CHECK_CODE_KEY);
		}
	}

	@RequestMapping("/login")
	@GlobalInterceptor(checkParams = true, checkLogin = false)
	public ResponseVO login(HttpSession session,
							   @VerifyParam(required = true) String email,
							   @VerifyParam(required = true) String password,
							   @VerifyParam(required = true) String checkCode) {
		try {
			if (!checkCode.equalsIgnoreCase((String) session.getAttribute(Constants.CHECK_CODE_KEY))) {
				throw new BusinessException("图片验证码不正确");
			}
			SessionWebUserDto sessionWebUserDto = userInfoService.login(email, password);
			session.setAttribute(Constants.SESSION_KEY, sessionWebUserDto);
			return getSuccessResponseVO(sessionWebUserDto);
		} finally {
			session.removeAttribute(Constants.CHECK_CODE_KEY);
		}
	}
	@RequestMapping("/resetPwd")
	@GlobalInterceptor(checkParams = true, checkLogin = false)
	public ResponseVO resetPwd(HttpSession session,
							   @VerifyParam(required = true, regex = VerifyRegexEnum.EMAIL, max = 150) String email,
							   @VerifyParam(required = true, regex = VerifyRegexEnum.PASSWORD) String password,
							   @VerifyParam(required = true) String checkCode,
							   @VerifyParam(required = true) String emailCode) {
		try {
			if (!checkCode.equalsIgnoreCase((String) session.getAttribute(Constants.CHECK_CODE_KEY))) {
				throw new BusinessException("图片验证码不正确");
			}
			userInfoService.resetPwd(email, password, emailCode);
			return getSuccessResponseVO(null);
		} finally {
			session.removeAttribute(Constants.CHECK_CODE_KEY);
		}
	}

	@RequestMapping("/getAvatar/{userId}")
	@GlobalInterceptor(checkParams = true, checkLogin = false)
	public void getAvatar(HttpServletResponse response, @VerifyParam(required = true) @PathVariable("userId") String userId) {
		String avatarFolderName = Constants.FILE_FOLDER_FILE + Constants.FILE_FOLDER_AVATAR_NAME;
		File folder = new File(appConfig.getProjectFolder() + avatarFolderName);
		if (!folder.exists()) {
			folder.mkdirs();
		}
		String avatarPath = appConfig.getProjectFolder() + avatarFolderName + userId + Constants.AVATAR_SUFFIX;
		File file = new File(avatarPath);
		if (!file.exists()) {
			if (!new File(appConfig.getProjectFolder() + avatarFolderName + Constants.AVATAR_DEFAULT).exists()) {
				printNoDefaultImage(response);
			}
			avatarPath = appConfig.getProjectFolder() + avatarFolderName + Constants.AVATAR_DEFAULT;
		}
		response.setContentType("image/jpg");
		readFile(response, avatarPath);
	}

	private void printNoDefaultImage(HttpServletResponse response) {
		response.setHeader(CONTENT_TYPE, CONTENT_TYPE_VALUE);
		response.setStatus(HttpStatus.OK.value());
        try (PrintWriter writer = response.getWriter()) {
            writer.print("请在头像目录下放置默认头像default_avatar.jpg");
        } catch (Exception e) {
            logger.error("输出无默认图失败", e);
        }
	}

//	@RequestMapping("/getUserInfo")
//	@GlobalInterceptor(checkParams = true)
//	public ResponseVO getUserInfo(HttpSession session) {
//		SessionWebUserDto sessionWebUserDto = getUserInfoFromSession(session);
//		return getSuccessResponseVO(sessionWebUserDto);
//	}

	@RequestMapping("/getUseSpace")
	@GlobalInterceptor
	public ResponseVO getUseSpace(HttpSession session) {
		SessionWebUserDto sessionWebUserDto = getUserInfoFromSession(session);
		UserSpaceDto userSpaceDto = redisComponent.getUserSpaceUse(sessionWebUserDto.getUserId());
		return getSuccessResponseVO(userSpaceDto);
	}

	@RequestMapping("/logout")
	public ResponseVO logout(HttpSession session) {
		session.invalidate();
		return getSuccessResponseVO(null);
	}

	@RequestMapping("/updateUserAvatar")
	@GlobalInterceptor
	public ResponseVO updateUserAvatar(HttpSession session, MultipartFile avatar) {
		SessionWebUserDto webUserDto = getUserInfoFromSession(session);
		String baseFolder = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE;
		File targetFileFolder = new File(baseFolder + Constants.FILE_FOLDER_AVATAR_NAME);
		if (!targetFileFolder.exists()) {
			targetFileFolder.mkdirs();
		}
		File targetFile = new File(targetFileFolder.getPath() + "/" + webUserDto.getUserId() + Constants.AVATAR_SUFFIX);
		try {
			avatar.transferTo(targetFile);
		} catch (Exception e) {
			logger.error("上传头像失败", e);
		}
		UserInfo userInfo = new UserInfo();
		userInfo.setQqAvatar("");
		userInfoService.updateUserInfoByUserId(userInfo, webUserDto.getUserId());
		webUserDto.setAvatar(null);
		session.setAttribute(Constants.SESSION_KEY, webUserDto);
		return getSuccessResponseVO(null);
	}

	@RequestMapping("/updatePassword")
	@GlobalInterceptor(checkParams = true)
	public ResponseVO updatePassword(HttpSession session,
									 @VerifyParam(required = true,
											 regex = VerifyRegexEnum.PASSWORD,
											 min = 8, max = 18) String password) {
		SessionWebUserDto sessionWebUserDto = getUserInfoFromSession(session);
		UserInfo userInfo = new UserInfo();
		userInfo.setPassword(StringTools.encodeByMd5(password));
		userInfoService.updateUserInfoByUserId(userInfo, sessionWebUserDto.getUserId());
		return getSuccessResponseVO(null);
	}
}