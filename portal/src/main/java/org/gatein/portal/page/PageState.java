/*
 * Copyright (C) 2012 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.gatein.portal.page;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.xml.namespace.QName;

import juzu.request.Phase;
import org.gatein.portal.mop.customization.CustomizationService;
import org.gatein.portal.mop.hierarchy.NodeContext;
import org.gatein.portal.mop.hierarchy.NodeModel;
import org.gatein.portal.mop.layout.ElementState;
import org.gatein.portal.portlet.PortletAppManager;

/**
 * Encapsulate state and operations on a page.
 *
 * @author <a href="mailto:julien.viet@exoplatform.com">Julien Viet</a>
 */
public class PageState implements NodeModel<NodeState, ElementState>, Iterable<Map.Entry<String, WindowState>> {

    /** . */
    private static final Map<QName, String[]> NO_PARAMETERS = Collections.emptyMap();

    /** . */
    final PortletAppManager portletManager;

    /** . */
    final CustomizationService customizationService;

    /** The canonical navigation path. */
    public final String path;

    /** A map of name -> window. */
    private final HashMap<String, WindowState> windowMap;

    /** The page render parameters. */
    private Map<QName, String[]> parameters;

    /** Windows iteration. */
    public final Iterable<WindowState> windows;

    public PageState(CustomizationService customizationService, PortletAppManager portletManager, String path) {
        this.customizationService = customizationService;
        this.portletManager = portletManager;
        this.path = path;
        this.windowMap = new HashMap<String, WindowState>();
        this.windows = windowMap.values();
        this.parameters = NO_PARAMETERS;
    }

    public PageState(PageState that) {

        // Clone the windows
        HashMap<String, WindowState> windowMap = new HashMap<String, WindowState>(that.windowMap);
        for (Map.Entry<String, WindowState> entry : windowMap.entrySet()) {
            WindowState window = entry.getValue();
            entry.setValue(new WindowState(window, this));
        }

        // Clone the parameters
        Map<QName, String[]> parameters;
        if (that.parameters.size() > 0) {
            parameters = new HashMap<QName, String[]>(that.parameters);
        } else {
            parameters = NO_PARAMETERS;
        }

        //
        this.customizationService = that.customizationService;
        this.portletManager = that.portletManager;
        this.path = that.path;
        this.windowMap = windowMap;
        this.windows = windowMap.values();
        this.parameters = parameters;
    }

    public WindowState get(String name) {
        return windowMap.get(name);
    }

    @Override
    public Iterator<Map.Entry<String, WindowState>> iterator() {
        return windowMap.entrySet().iterator();
    }

    public Iterable<Map.Entry<QName, String[]>> parameters() {
        return parameters.entrySet();
    }

    public Map<QName, String[]> getParameters() {
        return parameters;
    }

    public String[] getParameter(QName name) {
        return parameters.get(name);
    }

    public void setParameter(QName name, String[] value) {
        if (value.length == 0) {
            if (parameters != NO_PARAMETERS) {
                parameters.remove(name);
            }
        } else {
            if (parameters == NO_PARAMETERS) {
                parameters = new HashMap<QName, String[]>();
            }
            parameters.put(name, value);
        }
    }

    public void setParameters(Map<QName, String[]> next) {
        if (next.size() == 0) {
            if (parameters != NO_PARAMETERS) {
                parameters.clear();
            }
        } else {
            if (parameters == NO_PARAMETERS) {
                parameters = new HashMap<QName, String[]>();
            }
            parameters.putAll(next);
        }
    }

    //

    public Phase.View.Dispatch getDispatch() {
        Phase.View.Dispatch view = Controller_.index(path, null, null, null, null);
        for (WindowState w : windows) {
            w.encode(view);
        }
        for (Map.Entry<QName, String[]> parameter : parameters.entrySet()) {
            view.setParameter(parameter.getKey().getLocalPart(), parameter.getValue());
        }
        return view;
    }

    Phase.View.Dispatch getDispatch(String action, String target) {
        Phase.View.Dispatch view = Controller_.index(path, action, null, null, null);
        for (WindowState w : windows) {
            w.encode(view);
        }
        return view;
    }

    @Override
    public NodeContext<NodeState, ElementState> getContext(NodeState node) {
        return node.context;
    }

    @Override
    public NodeState create(NodeContext<NodeState, ElementState> context) {
        if (context.getState() instanceof ElementState.Window) {
            NodeState window = new NodeState(context);
            WindowState windowState = new WindowState(window, this);
            windowMap.put(windowState.name, windowState);
            return window;
        } else {
            return new NodeState(context);
        }
    }
}
