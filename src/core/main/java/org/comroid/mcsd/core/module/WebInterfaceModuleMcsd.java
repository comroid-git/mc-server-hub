package org.comroid.mcsd.core.module;

import lombok.Value;
import org.comroid.mcsd.core.entity.Server;

@Value
public class WebInterfaceModuleMcsd extends McsdCommandModule {
    public static final Factory<WebInterfaceModuleMcsd> Factory = new Factory<>(WebInterfaceModuleMcsd.class) {
        @Override
        public WebInterfaceModuleMcsd create(Server server) {
            return new WebInterfaceModuleMcsd(server);
        }
    };

    private WebInterfaceModuleMcsd(Server server) {
        super(server);
    }
}
