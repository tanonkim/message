package com.message.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Message;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.springframework.boot.activemq.autoconfigure.ActiveMQConnectionFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MessageConversionException;
import org.springframework.jms.support.converter.MessageConverter;

@EnableJms
@Configuration
public class ActiveMqConfig {

    @Bean
    public ActiveMQConnectionFactoryCustomizer activeMQConnectionFactoryCustomizer() {
        return factory -> factory.setTrustAllPackages(true);
    }

    @Bean
    public MessageConverter jacksonJmsMessageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        return new MessageConverter() {
            @Override
            public Message toMessage(Object object, Session session) throws jakarta.jms.JMSException {
                try {
                    String json = mapper.writeValueAsString(object);
                    TextMessage message = session.createTextMessage(json);
                    message.setStringProperty("_type", object.getClass().getName());
                    return message;
                } catch (Exception e) {
                    throw new MessageConversionException("Cannot convert to JMS message: " + object.getClass(), e);
                }
            }

            @Override
            public Object fromMessage(Message message) throws jakarta.jms.JMSException {
                if (message instanceof TextMessage textMessage) {
                    try {
                        String typeId = message.getStringProperty("_type");
                        Class<?> targetClass = Class.forName(typeId);
                        return mapper.readValue(textMessage.getText(), targetClass);
                    } catch (Exception e) {
                        throw new MessageConversionException("Cannot convert from JMS message", e);
                    }
                }
                throw new MessageConversionException("Unsupported JMS message type: " + message.getClass());
            }
        };
    }

    @Bean
    public JmsTemplate jmsTemplate(ConnectionFactory connectionFactory, MessageConverter jacksonJmsMessageConverter) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setMessageConverter(jacksonJmsMessageConverter);
        return template;
    }

    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(ConnectionFactory connectionFactory, MessageConverter jacksonJmsMessageConverter) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jacksonJmsMessageConverter);
        factory.setConcurrency("1-5");
        factory.setSessionTransacted(true);
        return factory;
    }
}
