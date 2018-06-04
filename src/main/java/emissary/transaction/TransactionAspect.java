package emissary.transaction;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.Factory;
import emissary.core.IBaseDataObject;
import emissary.pickup.WorkBundle;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;

@Aspect
@SuppressWarnings("unused")
public class TransactionAspect {

    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final TransactionService service;

    public TransactionAspect() throws IOException {
        this(null);
    }

    public TransactionAspect(TransactionService service) throws IOException {
        if (service != null) {
            this.service = service;
        } else {
            Configurator configurator = ConfigUtil.getConfigInfo(TransactionAspect.class);
            String txService = configurator.findStringEntry("HANDLER_CLASS", emissary.transaction.TransactionService.class.getName());
            this.service = (TransactionService) Factory.create(txService);
        }
    }

    @Pointcut("execution(* emissary.pickup.WorkSpace.addOutboundBundle(emissary.pickup.WorkBundle))")
    public void createWB() {}

    @Pointcut("execution(* emissary.pickup.file.FilePickUpClient.processBundle(emissary.pickup.WorkBundle))")
    public void processWB() {}

    @Pointcut("execution(* emissary.pickup.file.FilePickUpClient.dataObjectCreated(emissary.core.IBaseDataObject, java.io.File))")
    public void startIBDO() {}

    @Pointcut("execution(* emissary.output.filter.AbstractFilter+.filter(java.util.List, java.util.Map))")
    public void updateIBDO() {}

    @Pointcut("execution(* emissary.core.MobileAgent.logAgentCompletion(emissary.core.IBaseDataObject))")
    public void completeIBDO() {}

    @Pointcut("execution(* emissary.server.EmissaryServer.startServer(..))")
    public void startup() {}

    @Pointcut("execution(* emissary.server.EmissaryServer.stopServer(java.lang.String, boolean))")
    public void shutdown() {}

    @Before("createWB() && args(wb)")
    public void createWB(WorkBundle wb) {
        try {
            service.create(wb);
        } catch (Exception e) {
            log.error("There was an error processing transaction", e);
        }
    }

    @Before("processWB() && args(wb)")
    public void startWB(WorkBundle wb) {
        try {
            service.start(wb);
        } catch (Exception e) {
            log.error("There was an error processing transaction", e);
        }
    }

    @AfterReturning(pointcut = "processWB()", returning = "success")
    public void completeWB(JoinPoint jp, boolean success) {
        try {
            WorkBundle wb = (WorkBundle) jp.getArgs()[0];
            if (success) {
                service.commit(wb);
            } else {
                service.fail(wb);
            }
        } catch (Exception e) {
            log.error("There was an error processing transaction", e);
        }
    }

    @After("startIBDO() && args(ibdo, path)")
    public void startIBDO(IBaseDataObject ibdo, File path) {
        try {
            service.start(ibdo);
        } catch (Exception e) {
            log.error("There was an error processing transaction", e);
        }
    }

    @After("updateIBDO() && args(ibdos, params)")
    public void updateIBDO(List<IBaseDataObject> ibdos, Map<String, Object> params) {
        try {
            service.update(ibdos, params);
        } catch (Exception e) {
            log.error("There was an error processing transaction", e);
        }
    }

    @Before("completeIBDO() && args(ibdo)")
    public void completeIBDO(IBaseDataObject ibdo) {
        try {
            service.commit(ibdo);
        } catch (Exception e) {
            log.error("There was an error processing transaction", e);
        }
    }

    @After("startup()")
    public void start() {
        try {
            service.startup();
        } catch (Exception e) {
            log.error("There was an error during startup", e);
        }
    }

    @After("shutdown()")
    public void stop() {
        try {
            service.shutdown();
        } catch (Exception e) {
            log.error("There was an error during shutdown", e);
        }
    }

}
