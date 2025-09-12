package com.afristays.apigateway.config;

import com.afristays.apigateway.service.IpBlacklistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class SecurityInitializer implements ApplicationRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(SecurityInitializer.class);
    
    @Autowired
    private IpBlacklistService ipBlacklistService;
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        logger.info("Initializing security components...");
        
        // Initialize IP blacklist and whitelist from configuration
        ipBlacklistService.initializeLists();
        
        logger.info("Security components initialized successfully");
    }
}
