package com.chonbosmods.action;

import java.util.Map;

public interface DialogueAction {
    void execute(ActionContext context, Map<String, String> params);
}
