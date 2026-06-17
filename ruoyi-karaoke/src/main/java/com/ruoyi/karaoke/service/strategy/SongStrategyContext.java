package com.ruoyi.karaoke.service.strategy;

import com.ruoyi.karaoke.model.WebSocketConsole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Map;
import static com.ruoyi.karaoke.enums.ConsoleType.getStrategy;


/**
 * 策略上下文
 *
 * @author jo
 */
@Service
public class SongStrategyContext {


    @Autowired
    private Map<String, SongClientStrategyInterface> strategyInterfaceMap;

    /**
     * 执行策略
     *
     * @param
     */
    public WebSocketConsole deal(WebSocketConsole console) {
        String strategyName = getStrategy(console.getCode());
        if (strategyName == null) {
            return console;
        }
        SongClientStrategyInterface strategy = strategyInterfaceMap.get(strategyName);
        if (strategy == null) {
            return console;
        }
        return strategy.deal(console);
    }



}