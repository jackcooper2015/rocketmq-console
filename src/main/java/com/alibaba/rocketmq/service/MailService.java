package com.alibaba.rocketmq.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import static org.apache.commons.lang.StringUtils.isEmpty;
/**
 * 邮件发送公共类
 *
 * @author yaojinwei
 * @since 2016/9/23
 */
@Service
public class MailService extends AbstractService implements InitializingBean{

    protected static final Logger LOG = LoggerFactory.getLogger(MailService.class);

    // mail sender
    private JavaMailSenderImpl mailSender = new JavaMailSenderImpl();

    /**
     * 发送html邮件
     *
     * @throws javax.mail.MessagingException
     * @throws javax.mail.internet.AddressException
     */
    public void sendHtmlMail(String from, String[] to, String title, String text)
            throws AddressException, MessagingException {

        long start = System.currentTimeMillis();

        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper messageHelper = new MimeMessageHelper(mimeMessage, true, "GBK");

        InternetAddress[] toArray = new InternetAddress[to.length];
        for (int i = 0; i < to.length; i++) {
            toArray[i] = new InternetAddress(to[i]);
        }

        messageHelper.setFrom(new InternetAddress(from));
        messageHelper.setTo(toArray);
        messageHelper.setSubject(title);
        messageHelper.setText(text, true);
        mimeMessage = messageHelper.getMimeMessage();
        mailSender.send(mimeMessage);
        long end = System.currentTimeMillis();
        LOG.info("send mail start:" + start + " end :" + end);
    }

    /**
     * 设置邮箱host，用户名和密码
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        mailSender.setHost(configureInitializer.getEmailHost());

        if (!isEmpty(configureInitializer.getEmailUser())) {
            mailSender.setUsername(configureInitializer.getEmailUser());
        }

        if (!isEmpty(configureInitializer.getEmailPassword())) {
            mailSender.setPassword(configureInitializer.getEmailPassword());
        }

        if (!isEmpty(configureInitializer.getEmailPort())) {

            try {

                Integer port = Integer.parseInt(configureInitializer.getEmailPort());
                mailSender.setPort(port);
            } catch (Exception e) {
                LOG.error(e.toString());
            }
        }
    }
}
