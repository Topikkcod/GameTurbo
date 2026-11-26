package com.floatx.gameturbo;

interface IUserService {
    void destroy() = 16777114;
    void injectMotion(int displayId, int action, float x, float y, long downTime, long eventTime) = 1;
    void injectKey(int displayId, int keyCode, int action) = 2;
    void injectText(int displayId, String text) = 3;
    String exec(String cmd) = 4;
}
