/**
 * Copyright (C) 2009 eXo Platform SAS.
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

package org.exoplatform.portal.pom.data;

import org.exoplatform.portal.config.UserACL;
import org.exoplatform.portal.config.model.ApplicationState;
import org.exoplatform.portal.config.model.ApplicationType;
import org.exoplatform.portal.pom.data.BodyType;
import org.exoplatform.portal.config.model.ModelChange;
import org.exoplatform.portal.config.model.PersistentApplicationState;
import org.exoplatform.portal.config.model.PortalConfig;
import org.exoplatform.portal.config.model.TransientApplicationState;
import org.exoplatform.portal.config.model.gadget.GadgetId;
import org.exoplatform.portal.pom.config.Utils;
import static org.exoplatform.portal.pom.config.Utils.join;
import static org.exoplatform.portal.pom.config.Utils.split;

import org.exoplatform.portal.config.model.portlet.PortletId;
import org.exoplatform.portal.config.model.wsrp.WSRPId;
import org.exoplatform.portal.pom.config.POMSession;
import org.exoplatform.portal.pom.data.ApplicationData;
import org.exoplatform.portal.pom.data.BodyData;
import org.exoplatform.portal.pom.data.ComponentData;
import org.exoplatform.portal.pom.data.ContainerData;
import org.exoplatform.portal.pom.data.DashboardData;
import org.exoplatform.portal.pom.data.ModelData;
import org.exoplatform.portal.pom.data.NavigationNodeContainerData;
import org.exoplatform.portal.pom.data.PageData;
import org.exoplatform.portal.pom.data.PortalData;
import org.exoplatform.portal.pom.spi.gadget.Gadget;
import org.exoplatform.portal.pom.spi.portlet.Preferences;
import org.exoplatform.portal.pom.spi.wsrp.WSRPState;
import org.gatein.mop.api.Attributes;
import org.gatein.mop.api.content.ContentType;
import org.gatein.mop.api.content.Customization;
import org.gatein.mop.api.workspace.Navigation;
import org.gatein.mop.api.workspace.ObjectType;
import org.gatein.mop.api.workspace.Site;
import org.gatein.mop.api.workspace.Workspace;
import org.gatein.mop.api.workspace.WorkspaceObject;
import org.gatein.mop.api.workspace.link.Link;
import org.gatein.mop.api.workspace.link.PageLink;
import org.gatein.mop.api.workspace.ui.UIBody;
import org.gatein.mop.api.workspace.ui.UIComponent;
import org.gatein.mop.api.workspace.ui.UIContainer;
import org.gatein.mop.api.workspace.ui.UIWindow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * @author <a href="mailto:julien.viet@exoplatform.com">Julien Viet</a>
 * @version $Revision$
 */
public class Mapper
{

   /** . */
   private static final Set<String> portalPropertiesBlackList =
      new HashSet<String>(Arrays.asList("jcr:uuid", "jcr:primaryType", MappedAttributes.LOCALE.getName(),
         MappedAttributes.ACCESS_PERMISSIONS.getName(), MappedAttributes.EDIT_PERMISSION.getName(),
         MappedAttributes.SKIN.getName(), MappedAttributes.TITLE.getName(), MappedAttributes.CREATOR.getName(),
         MappedAttributes.MODIFIER.getName()));

   /** . */
   private static final Set<String> windowPropertiesBlackList =
      new HashSet<String>(Arrays.asList("jcr:uuid", "jcr:primaryType", MappedAttributes.TYPE.getName(),
         MappedAttributes.THEME.getName(), MappedAttributes.TITLE.getName(), MappedAttributes.ACCESS_PERMISSIONS
            .getName(), MappedAttributes.SHOW_INFO_BAR.getName(), MappedAttributes.SHOW_STATE.getName(),
         MappedAttributes.SHOW_MODE.getName(), MappedAttributes.DESCRIPTION.getName(), MappedAttributes.ICON.getName(),
         MappedAttributes.WIDTH.getName(), MappedAttributes.HEIGHT.getName()));

   /** . */
   private final POMSession session;

   public Mapper(POMSession session)
   {
      this.session = session;
   }

   public NavigationData load(Navigation src)
   {
      return load(src, NavigationData.class);
   }

   private <T extends NavigationNodeContainerData> T load(Navigation src, Class<T> type)
   {

      //
      ArrayList<NavigationNodeData> children = new ArrayList<NavigationNodeData>(src.getChildren().size());
      for (Navigation srcChild : src.getChildren())
      {
         NavigationNodeData dstChild = load(srcChild, NavigationNodeData.class);
         children.add(dstChild);
      }

      //
      T dst;
      if (type == NavigationData.class)
      {
         Site site = src.getSite();
         String ownerType = getOwnerType(site.getObjectType());
         String ownerId = site.getName();
         Attributes attrs = src.getAttributes();
         NavigationData dstNav = new NavigationData(
            src.getObjectId(),
            ownerType,
            ownerId,
            attrs.getValue(MappedAttributes.DESCRIPTION),
            attrs.getValue(MappedAttributes.CREATOR),
            attrs.getValue(MappedAttributes.MODIFIER),
            attrs.getValue(MappedAttributes.PRIORITY, 1),
            children);
         dst = (T)dstNav;
      }
      else if (type == NavigationNodeData.class)
      {
         Attributes attrs = src.getAttributes();
         String pageReference = null;
         Link link = src.getLink();
         if (link instanceof PageLink)
         {
            PageLink pageLink = (PageLink)link;
            org.gatein.mop.api.workspace.Page target = pageLink.getPage();
            if (target != null)
            {
               Site site = target.getSite();
               ObjectType<? extends Site> siteType = site.getObjectType();
               pageReference = getOwnerType(siteType) + "::" + site.getName() + "::" + target.getName();
            }
         }
         NavigationNodeData dstNode = new NavigationNodeData(
            src.getObjectId(),
            attrs.getValue(MappedAttributes.URI),
            attrs.getValue(MappedAttributes.LABEL),
            attrs.getValue(MappedAttributes.ICON),
            src.getName(),
            attrs.getValue(MappedAttributes.START_PUBLICATION_DATE),
            attrs.getValue(MappedAttributes.END_PUBLICATION_DATE),
            attrs.getValue(MappedAttributes.SHOW_PUBLICATION_DATE, false),
            attrs.getValue(MappedAttributes.VISIBLE, true),
            pageReference,
            children
         );

         dst = (T)dstNode;
      }
      else
      {
         throw new AssertionError();
      }

      //
      return dst;
   }

   public void save(NavigationData src, Navigation dst)
   {
      save((NavigationNodeContainerData)src, dst);
   }

   private void save(NavigationNodeContainerData src, Navigation dst)
   {
      if (src instanceof NavigationNodeData)
      {
         NavigationNodeData node = (NavigationNodeData)src;
         Workspace workspace = dst.getSite().getWorkspace();
         String reference = node.getPageReference();
         if (reference != null)
         {
            String[] pageChunks = split("::", reference);
            ObjectType<? extends Site> siteType = parseSiteType(pageChunks[0]);
            Site site = workspace.getSite(siteType, pageChunks[1]);
            org.gatein.mop.api.workspace.Page target = site.getRootPage().getChild("pages").getChild(pageChunks[2]);
            PageLink link = dst.linkTo(ObjectType.PAGE_LINK);
            link.setPage(target);
         }

         //
         Attributes attrs = dst.getAttributes();
         attrs.setValue(MappedAttributes.URI, node.getURI());
         attrs.setValue(MappedAttributes.LABEL, node.getLabel());
         attrs.setValue(MappedAttributes.ICON, node.getIcon());
         attrs.setValue(MappedAttributes.START_PUBLICATION_DATE, node.getStartPublicationDate());
         attrs.setValue(MappedAttributes.END_PUBLICATION_DATE, node.getEndPublicationDate());
         attrs.setValue(MappedAttributes.SHOW_PUBLICATION_DATE, node.getShowPublicationDate());
         attrs.setValue(MappedAttributes.VISIBLE, node.isVisible());
      }
      else if (src instanceof NavigationData)
      {
         NavigationData pageNav = (NavigationData)src;

         //
         Attributes attrs = dst.getAttributes();
         attrs.setValue(MappedAttributes.PRIORITY, pageNav.getPriority());
         attrs.setValue(MappedAttributes.CREATOR, pageNav.getCreator());
         attrs.setValue(MappedAttributes.MODIFIER, pageNav.getModifier());
         attrs.setValue(MappedAttributes.DESCRIPTION, pageNav.getDescription());
      }
      else
      {
         throw new AssertionError();
      }

      //
      Set<String> savedSet = new HashSet<String>();
      for (NavigationNodeData node : src.getNodes())
      {
         String srcId = node.getStorageId();
         Navigation dstChild;
         if (srcId != null)
         {
            dstChild = session.findObjectById(ObjectType.NAVIGATION, srcId);
         }
         else
         {
            dstChild = dst.getChild(node.getName());
            if (dstChild == null)
            {
               dstChild = dst.addChild(node.getName());
            }
            srcId = dstChild.getObjectId();
         }
         save(node, dstChild);
         savedSet.add(srcId);
      }
      for (Iterator<? extends Navigation> i = dst.getChildren().iterator(); i.hasNext();)
      {
         Navigation dstChild = i.next();
         if (!savedSet.contains(dstChild.getObjectId()))
         {
            i.remove();
         }
      }
   }

   public PortalData load(Site src)
   {
      String type = Mapper.getOwnerType(src.getObjectType());
      Attributes attrs = src.getAttributes();

      //
      org.gatein.mop.api.workspace.Page template = src.getRootNavigation().getTemplate();
      UIContainer srcLayout = template.getRootComponent();

      //
      Map<String, String> properties = new HashMap<String, String>();
      load(attrs, properties, portalPropertiesBlackList);

      //
      List<ComponentData> layoutChildren = loadChildren(srcLayout);
      ContainerData layout = load(srcLayout, layoutChildren);

      //
      return new PortalData(
         src.getObjectId(),
         src.getName(),
         type,
         attrs.getValue(MappedAttributes.LOCALE),
         Collections.unmodifiableList(Arrays.asList(split("|", attrs.getValue(MappedAttributes.ACCESS_PERMISSIONS, "")))),
         attrs.getValue(MappedAttributes.EDIT_PERMISSION),
         Collections.unmodifiableMap(properties),
         attrs.getValue(MappedAttributes.SKIN),
         attrs.getValue(MappedAttributes.TITLE),
         layout,
         attrs.getValue(MappedAttributes.CREATOR),
         attrs.getValue(MappedAttributes.MODIFIER));
   }

   public void save(PortalData src, Site dst)
   {
      if (src.getStorageId() != null && !src.getStorageId().equals(dst.getObjectId()))
      {
         String msg =
            "Attempt to save a site " + src.getType() + "/" + src.getName() + " on the wrong target site "
               + dst.getObjectType() + "/" + dst.getName();
         throw new IllegalArgumentException(msg);
      }

      //
      Attributes attrs = dst.getAttributes();
      attrs.setValue(MappedAttributes.LOCALE, src.getLocale());
      attrs.setValue(MappedAttributes.ACCESS_PERMISSIONS, join("|", src.getAccessPermissions()));
      attrs.setValue(MappedAttributes.EDIT_PERMISSION, src.getEditPermission());
      attrs.setValue(MappedAttributes.SKIN, src.getSkin());
      attrs.setValue(MappedAttributes.TITLE, src.getTitle());
      attrs.setValue(MappedAttributes.CREATOR, src.getCreator());
      attrs.setValue(MappedAttributes.MODIFIER, src.getModifier());
      if (src.getProperties() != null)
      {
         save(src.getProperties(), attrs);
      }

      //
      org.gatein.mop.api.workspace.Page templates = dst.getRootPage().getChild("templates");
      org.gatein.mop.api.workspace.Page template = templates.getChild("default");
      if (template == null)
      {
         template = templates.addChild("default");
      }

      //
      ContainerData srcContainer = src.getPortalLayout();
      UIContainer dstContainer = template.getRootComponent();

      //
      save(srcContainer, dstContainer);
      saveChildren(srcContainer, dstContainer);

      //
      dst.getRootNavigation().setTemplate(template);
   }

   public PageData load(org.gatein.mop.api.workspace.Page src)
   {
      Site site = src.getSite();
      String ownerType = getOwnerType(site.getObjectType());
      String ownerId = site.getName();
      String name = src.getName();
      List<ComponentData> children = loadChildren(src.getRootComponent());
      
      UIContainer srcRoot = src.getRootComponent();
      UIComponent srcChild = srcRoot.get(0);
      
      Attributes attrs = srcChild.getAttributes();

      //
      return new PageData(
         src.getObjectId(),
         null,
         name,
         null,
         null,
         null,
         attrs.getValue(MappedAttributes.FACTORY_ID),
         attrs.getValue(MappedAttributes.TITLE),
         null,
         null,
         null,
         Utils.safeImmutableList(split("|", attrs.getValue(MappedAttributes.ACCESS_PERMISSIONS))),
         children,
         ownerType,
         ownerId,
         attrs.getValue(MappedAttributes.EDIT_PERMISSION),
         attrs.getValue(MappedAttributes.SHOW_MAX_WINDOW, false),
         attrs.getValue(MappedAttributes.CREATOR),
         attrs.getValue(MappedAttributes.MODIFIER)
      );
   }

   public List<ModelChange> save(PageData src, Site site, String name)
   {
      org.gatein.mop.api.workspace.Page root = site.getRootPage();
      org.gatein.mop.api.workspace.Page pages = root.getChild("pages");
      org.gatein.mop.api.workspace.Page dst = pages.getChild(name);

      //
      LinkedList<ModelChange> changes = new LinkedList<ModelChange>();

      //
      if (dst == null)
      {
         dst = pages.addChild(name);
         changes.add(new ModelChange.Create(src));
      }
      else
      {
         changes.add(new ModelChange.Update(src));
      }

      //
      Attributes attrs = dst.getAttributes();
      attrs.setValue(MappedAttributes.TITLE, src.getTitle());
      attrs.setValue(MappedAttributes.FACTORY_ID, src.getFactoryId());
      attrs.setValue(MappedAttributes.ACCESS_PERMISSIONS, join("|", src.getAccessPermissions()));
      attrs.setValue(MappedAttributes.EDIT_PERMISSION, src.getEditPermission());
      attrs.setValue(MappedAttributes.SHOW_MAX_WINDOW, src.isShowMaxWindow());
      attrs.setValue(MappedAttributes.CREATOR, src.getCreator());
      attrs.setValue(MappedAttributes.MODIFIER, src.getModifier());

      //
      changes.addAll(saveChildren(src, dst.getRootComponent()));

      //
      return changes;
   }

   private ContainerData load(UIContainer src, List<ComponentData> children)
   {
      Attributes attrs = src.getAttributes();
      return new ContainerData(
         src.getObjectId(),
         attrs.getValue(MappedAttributes.ID),
         attrs.getValue(MappedAttributes.NAME),
         attrs.getValue(MappedAttributes.ICON),
         attrs.getValue(MappedAttributes.DECORATOR),
         attrs.getValue(MappedAttributes.TEMPLATE),
         attrs.getValue(MappedAttributes.FACTORY_ID),
         attrs.getValue(MappedAttributes.TITLE),
         attrs.getValue(MappedAttributes.DESCRIPTION),
         attrs.getValue(MappedAttributes.WIDTH),
         attrs.getValue(MappedAttributes.HEIGHT),
         Utils.safeImmutableList(split("|", attrs.getValue(MappedAttributes.ACCESS_PERMISSIONS))),
         children
      );
   }

   private List<ComponentData> loadChildren(UIContainer src)
   {
      ArrayList<ComponentData> children = new ArrayList<ComponentData>(src.size());
      for (UIComponent component : src)
      {

         // Obtain a model object from the ui component
         ComponentData mo;
         if (component instanceof UIContainer)
         {
            UIContainer srcContainer = (UIContainer)component;
            Attributes attrs = srcContainer.getAttributes();
            String type = attrs.getValue(MappedAttributes.TYPE);
            if ("dashboard".equals(type))
            {
               TransientApplicationState<Preferences> state = new TransientApplicationState<Preferences>();
               Site owner = src.getPage().getSite();
               state.setOwnerType(getOwnerType(owner.getObjectType()));
               state.setOwnerId(owner.getName());
               mo = new ApplicationData<Preferences, PortletId>(
                  srcContainer.getObjectId(),
                  component.getName(),
                  ApplicationType.PORTLET,
                  state,
                  new PortletId("dashboard", "DashboardPortlet"),
                  null,
                  null,
                  null,
                  null,
                  false,
                  false,
                  false,
                  null,
                  null,
                  null,
                  Collections.<String, String>emptyMap(),
                  Collections.singletonList(UserACL.EVERYONE));
               // Julien : the everyone is bad but having null permission
               // means the same thing cf {@link UIPortalComponent} class
               // we need to solve that somehow
            }
            else
            {
               List<ComponentData> dstChildren = loadChildren(srcContainer);
               mo = load(srcContainer, dstChildren);
            }
         }
         else if (component instanceof UIWindow)
         {
            UIWindow window = (UIWindow)component;
            ApplicationData application = load(window);
            mo = application;
         }
         else if (component instanceof UIBody)
         {
            mo = new BodyData(component.getObjectId(), BodyType.PAGE);
         }
         else
         {
            throw new AssertionError();
         }

         // Add among children
         children.add(mo);
      }
      return children;
   }

   private void save(ContainerData src, UIContainer dst)
   {
      Attributes dstAttrs = dst.getAttributes();
      dstAttrs.setValue(MappedAttributes.ID, src.getId());
      dstAttrs.setValue(MappedAttributes.TYPE, src instanceof DashboardData ? "dashboard" : null);
      dstAttrs.setValue(MappedAttributes.TITLE, src.getTitle());
      dstAttrs.setValue(MappedAttributes.ICON, src.getIcon());
      dstAttrs.setValue(MappedAttributes.TEMPLATE, src.getTemplate());
      dstAttrs.setValue(MappedAttributes.ACCESS_PERMISSIONS, join("|", src.getAccessPermissions()));
      dstAttrs.setValue(MappedAttributes.FACTORY_ID, src.getFactoryId());
      dstAttrs.setValue(MappedAttributes.DECORATOR, src.getDecorator());
      dstAttrs.setValue(MappedAttributes.DESCRIPTION, src.getDescription());
      dstAttrs.setValue(MappedAttributes.WIDTH, src.getWidth());
      dstAttrs.setValue(MappedAttributes.HEIGHT, src.getHeight());
      dstAttrs.setValue(MappedAttributes.NAME, src.getName());
   }

   private void save(ModelData src, WorkspaceObject dst, LinkedList<ModelChange> changes,
      Map<String, String> hierarchyRelationships)
   {
      if (src instanceof ContainerData)
      {
         save((ContainerData)src, (UIContainer)dst);
         saveChildren((ContainerData)src, (UIContainer)dst, changes, hierarchyRelationships);
      }
      else if (src instanceof ApplicationData)
      {
         save((ApplicationData<?, ?>)src, (UIWindow)dst);
      }
      else if (src instanceof BodyData)
      {
         // Stateless
      }
      else
      {
         throw new AssertionError("Was not expecting child " + src);
      }
   }

   /** . */
   private static final PortletId DASHBOARD_ID = new PortletId("dashboard", "DashboardPortlet");

   private LinkedList<ModelChange> saveChildren(final ContainerData src, UIContainer dst)
   {

      //
      LinkedList<ModelChange> changes = new LinkedList<ModelChange>();

      //
      Map<String, String> hierarchyRelationships = new HashMap<String, String>();

      //
      build(src, hierarchyRelationships);

      //
      saveChildren(src, dst, changes, Collections.unmodifiableMap(hierarchyRelationships));

      //
      return changes;
   }

   private void build(ContainerData parent, Map<String, String> hierarchyRelationships)
   {
      String parentId = parent.getStorageId();
      if (parentId != null)
      {
         for (ModelData child : parent.getChildren())
         {
            String childId = child.getStorageId();
            if (childId != null)
            {
               if (hierarchyRelationships.put(childId, parentId) != null)
               {
                  throw new AssertionError("The same object is present two times in the object hierarchy");
               }
               if (child instanceof ContainerData)
               {
                  build((ContainerData)child, hierarchyRelationships);
               }
            }
         }
      }
   }

   private void saveChildren(final ContainerData src, UIContainer dst, LinkedList<ModelChange> changes,
      Map<String, String> hierarchyRelationships)
   {
      final List<String> orders = new ArrayList<String>();
      final Map<String, ModelData> modelObjectMap = new HashMap<String, ModelData>();

      //
      for (ModelData srcChild : src.getChildren())
      {
         String srcId = srcChild.getStorageId();

         // Replace dashboard application by container if needed
         if (srcChild instanceof ApplicationData)
         {
            ApplicationData app = (ApplicationData)srcChild;
            if (app.getType() == ApplicationType.PORTLET)
            {
               PortletId ref = (PortletId)app.getRef();
               if (DASHBOARD_ID.equals(ref))
               {
                  if (app.getStorageId() != null)
                  {
                     UIContainer dstDashboard = session.findObjectById(ObjectType.CONTAINER, app.getStorageId());
                     srcChild = loadDashboard(dstDashboard);
                  }
                  else
                  {
                     srcChild = DashboardData.INITIAL_DASHBOARD;
                  }
               }
            }
         }

         //
         UIComponent dstChild;
         if (srcId != null)
         {
            dstChild = session.findObjectById(ObjectType.COMPONENT, srcId);
            if (dstChild == null)
            {
               throw new AssertionError("Could not find supposed present child with id " + srcId);
            }
            // julien : this can fail due to a bug in chromattic not implementing equals method properly
            // and is replaced with the foreach below
            /*
                    if (!dst.contains(dstChild)) {
                      throw new IllegalArgumentException("Attempt for updating a ui component " + session.pathOf(dstChild) +
                        "that is not present in the target ui container " + session.pathOf(dst));
                    }
            */
            boolean found = false;
            for (UIComponent child : dst)
            {
               if (child.getObjectId().equals(srcId))
               {
                  found = true;
                  break;
               }
            }
            if (!found)
            {
               if (hierarchyRelationships.containsKey(srcId))
               {
                  // It's a move operation, so we move the node first
                  dst.add(dstChild);
               }
               else
               {
                  throw new IllegalArgumentException("Attempt for updating a ui component " + session.pathOf(dstChild)
                     + "that is not present in the target ui container " + session.pathOf(dst));
               }
            }

            //
            changes.add(new ModelChange.Update(srcChild));
         }
         else
         {
            String name = srcChild.getStorageName();
            if (name == null)
            {
               // We manufacture one name
               name = UUID.randomUUID().toString();
            }
            if (srcChild instanceof ContainerData)
            {
               dstChild = dst.add(ObjectType.CONTAINER, name);
            }
            else if (srcChild instanceof ApplicationData)
            {
               dstChild = dst.add(ObjectType.WINDOW, name);
            }
            else if (srcChild instanceof BodyData)
            {
               dstChild = dst.add(ObjectType.BODY, name);
            }
            else
            {
               throw new AssertionError("Was not expecting child " + srcChild);
            }
            changes.add(new ModelChange.Create(srcChild));
         }

         //
         save(srcChild, dstChild, changes, hierarchyRelationships);

         //
         String dstId = dstChild.getObjectId();
         modelObjectMap.put(dstId, srcChild);
         orders.add(dstId);
      }

      // Take care of move operation that could be seen as a remove
      for (UIComponent dstChild : dst)
      {
         String dstId = dstChild.getObjectId();
         if (!modelObjectMap.containsKey(dstId) && hierarchyRelationships.containsKey(dstId))
         {
            String parentId = hierarchyRelationships.get(dstId);

            // Get the new parent
            UIContainer parent = session.findObjectById(ObjectType.CONTAINER, parentId);

            // Perform the move
            parent.add(dstChild);

            //
            changes.add(new ModelChange.Destroy(dstId));
         }
      }

      // Delete removed children
      for (Iterator<UIComponent> i = dst.iterator(); i.hasNext();)
      {
         UIComponent dstChild = i.next();
         String dstId = dstChild.getObjectId();
         if (!modelObjectMap.containsKey(dstId))
         {
            i.remove();
            changes.add(new ModelChange.Destroy(dstId));
         }
      }

      // Now sort children according to the order provided by the container
      // need to replace that with Collections.sort once the set(int index, E element) is implemented in Chromattic lists
      UIComponent[] a = dst.toArray(new UIComponent[dst.size()]);
      Arrays.sort(a, new Comparator<UIComponent>()
      {
         public int compare(UIComponent o1, UIComponent o2)
         {
            int i1 = orders.indexOf(o1.getObjectId());
            int i2 = orders.indexOf(o2.getObjectId());
            return i1 - i2;
         }
      });
      for (int j = 0; j < a.length; j++)
      {
         dst.add(j, a[j]);
      }
   }

   private <S, I> ApplicationData<S, I> load(UIWindow src)
   {
      Attributes attrs = src.getAttributes();

      //
      Customization<?> customization = src.getCustomization();

      //
      ContentType<?> contentType = customization.getType();

      //
      String customizationid = customization.getId();

      //
      String contentId = customization.getContentId();

      //
      I ref;
      ApplicationType<S, I> type;
      if (contentType == null || contentType == Preferences.CONTENT_TYPE)
      {
         int pos = contentId.indexOf('/');
         String applicationName = contentId.substring(0, pos);
         String portletName = contentId.substring(pos + 1);
         ref = (I)new PortletId(applicationName, portletName);
         type = (ApplicationType<S,I>)ApplicationType.PORTLET;
      }
      else if (contentType == Gadget.CONTENT_TYPE)
      {
         ref = (I)new GadgetId(contentId);
         type = (ApplicationType<S,I>)ApplicationType.GADGET;
      }
      else if (contentType == WSRPState.CONTENT_TYPE)
      {
         ref = (I)new WSRPId(contentId);
         type = (ApplicationType<S,I>)ApplicationType.WSRP_PORTLET;
      }
      else
      {
         throw new AssertionError("Unknown type: " + contentType);
      }

      //
      PersistentApplicationState<S> instanceState = new PersistentApplicationState<S>(customizationid);

      //
      HashMap<String, String> properties = new HashMap<String, String>();
      load(attrs, properties, windowPropertiesBlackList);

      //
      return new ApplicationData<S,I>(
         src.getObjectId(),
         src.getName(),
         type,
         instanceState,
         ref,
         null,
         attrs.getValue(MappedAttributes.TITLE),
         attrs.getValue(MappedAttributes.ICON),
         attrs.getValue(MappedAttributes.DESCRIPTION),
         attrs.getValue(MappedAttributes.SHOW_INFO_BAR),
         attrs.getValue(MappedAttributes.SHOW_STATE),
         attrs.getValue(MappedAttributes.SHOW_MODE),
         attrs.getValue(MappedAttributes.THEME),
         attrs.getValue(MappedAttributes.WIDTH),
         attrs.getValue(MappedAttributes.HEIGHT),
         Utils.safeImmutableMap(properties),
         Utils.safeImmutableList(split("|", attrs.getValue(MappedAttributes.ACCESS_PERMISSIONS)))
      );
   }

   public <S, I> void save(ApplicationData<S, I> src, UIWindow dst)
   {
      Attributes attrs = dst.getAttributes();
      attrs.setValue(MappedAttributes.THEME, src.getTheme());
      attrs.setValue(MappedAttributes.TITLE, src.getTitle());
      attrs.setValue(MappedAttributes.ACCESS_PERMISSIONS, join("|", src.getAccessPermissions()));
      attrs.setValue(MappedAttributes.SHOW_INFO_BAR, src.isShowInfoBar());
      attrs.setValue(MappedAttributes.SHOW_STATE, src.isShowApplicationState());
      attrs.setValue(MappedAttributes.SHOW_MODE, src.isShowApplicationMode());
      attrs.setValue(MappedAttributes.DESCRIPTION, src.getDescription());
      attrs.setValue(MappedAttributes.ICON, src.getIcon());
      attrs.setValue(MappedAttributes.WIDTH, src.getWidth());
      attrs.setValue(MappedAttributes.HEIGHT, src.getHeight());
      save(src.getProperties(), attrs);

      //
      ApplicationState<S> instanceState = src.getState();

      // We modify only transient portlet state
      // and we ignore any persistent portlet state
      if (instanceState instanceof TransientApplicationState)
      {

         //
         TransientApplicationState<S> transientState = (TransientApplicationState<S>)instanceState;

         // Attempt to get a site from the instance state
         Site site = null;
         if (transientState.getOwnerType() != null && transientState.getOwnerId() != null)
         {
            ObjectType<Site> siteType = parseSiteType(transientState.getOwnerType());
            site = session.getWorkspace().getSite(siteType, transientState.getOwnerId());
         }

         // The current site
         Site currentSite = dst.getPage().getSite();

         // If it is the same site than the current page
         // set null
         if (site == dst.getPage().getSite())
         {
            site = null;
         }

         // The content id
         String contentId;
         ContentType<S> contentType = src.getType().getContentType();
         if (contentType == Preferences.CONTENT_TYPE)
         {
            PortletId id = (PortletId)src.getRef();
            contentId = id.getApplicationName() + "/" + id.getPortletName();
         }
         else if (contentType == Gadget.CONTENT_TYPE)
         {
            GadgetId id = (GadgetId)src.getRef();
            contentId = id.getGadgetName();
         }
         else if (contentType == WSRPState.CONTENT_TYPE)
         {
            WSRPId id = (WSRPId)src.getRef();
            contentId = id.getUri();
         }
         else
         {
            throw new UnsupportedOperationException("Unsupported content type");
         }

         // The customization that we will inherit from if not null
         Customization<?> customization = null;

         // Now inspect the unique id
         String uniqueId = transientState.getUniqueId();
         if (uniqueId != null)
         {

            // This is a customized window
            if (uniqueId.startsWith("@"))
            {
               String id = uniqueId.substring(1);

               // It's another window, we get its customization
               if (!dst.getObjectId().equals(id))
               {
                  UIWindow window = session.findObjectById(ObjectType.WINDOW, id);
                  Customization<?> windowCustomization = window.getCustomization();
                  if (windowCustomization.getType().equals(contentType))
                  {
                     customization = windowCustomization;
                  }
               }
            }
            else
            {
               int pos = uniqueId.indexOf('#');
               if (pos == -1)
               {

                  // If it's a different site than the page one (it has to be at this point)
                  // then we get its customization
                  if (site != null)
                  {
                     customization = site.getCustomization(uniqueId);
                  }
                  else
                  {
                     customization = currentSite.getCustomization(uniqueId);

                     // If it does not exist we create it
                     if (customization == null)
                     {
                        customization = currentSite.customize(uniqueId, contentType, contentId, null);
                     }
                  }
               }
               else
               {

                  // Otherwise we get the page customization
                  String a = uniqueId.substring(0, pos);
                  String b = uniqueId.substring(pos + 1);
                  org.gatein.mop.api.workspace.Page page = site.getRootPage().getChild("pages").getChild(b);
                  customization = page.getCustomization(a);
               }
            }
         }

         // Destroy existing window previous customization
         if (dst.getCustomization() != null)
         {
            dst.getCustomization().destroy();
         }

         // If the existing customization is not null and matches the content id
         Customization<S> dstCustomization;
         if (customization != null && customization.getType().equals(contentType)
            && customization.getContentId().equals(contentId))
         {

            // Cast is ok as content type matches
            @SuppressWarnings("unchecked")
            Customization<S> bilto = (Customization<S>)customization;

            // If it's a customization of the current site we extend it
            if (bilto.getContext() == currentSite)
            {
               dstCustomization = dst.customize(bilto);
            }
            else
            {
               // Otherwise we clone it propertly
               S state = bilto.getVirtualState();
               dstCustomization = dst.customize(contentType, contentId, state);
            }
         }
         else
         {
            // Otherwise we create an empty customization
            dstCustomization = dst.customize(contentType, contentId, null);
         }

         // At this point we have customized the window
         // now if we have any additional state payload we must merge it
         // with the current state
         S state = ((TransientApplicationState<S>)instanceState).getContentState();
         if (state != null)
         {
            dstCustomization.setState(state);
         }
      }
   }

   public DashboardData loadDashboard(UIContainer container)
   {
      Attributes attrs = container.getAttributes();
      List<ComponentData> children = loadChildren(container);
      return new DashboardData(
         container.getObjectId(),
         attrs.getValue(MappedAttributes.ID),
         attrs.getValue(MappedAttributes.NAME),
         attrs.getValue(MappedAttributes.ICON),
         attrs.getValue(MappedAttributes.DECORATOR),
         attrs.getValue(MappedAttributes.TEMPLATE),
         attrs.getValue(MappedAttributes.FACTORY_ID),
         attrs.getValue(MappedAttributes.TITLE),
         attrs.getValue(MappedAttributes.DESCRIPTION),
         attrs.getValue(MappedAttributes.WIDTH),
         attrs.getValue(MappedAttributes.HEIGHT),
         Utils.safeImmutableList(split("|", attrs.getValue(MappedAttributes.ACCESS_PERMISSIONS))),
         children
      );
   }

   public void saveDashboard(DashboardData dashboard, UIContainer dst)
   {
      save(dashboard, dst);
      saveChildren(dashboard, dst);
   }

   public static String[] parseWindowId(String windowId)
   {
      int i0 = windowId.indexOf("#");
      int i1 = windowId.indexOf(":/", i0 + 1);
      String ownerType = windowId.substring(0, i0);
      String ownerId = windowId.substring(i0 + 1, i1);
      String persistenceid = windowId.substring(i1 + 2);
      String[] chunks = split("/", 2, persistenceid);
      chunks[0] = ownerType;
      chunks[1] = ownerId;
      return chunks;
   }

   private static void load(Attributes src, Map<String, String> dst, Set<String> blackList)
   {
      for (String name : src.getKeys())
      {
         if (!blackList.contains(name))
         {
            Object value = src.getObject(name);
            if (value instanceof String)
            {
               dst.put(name, (String)value);
            }
         }
      }
   }

   public static void save(Map<String, String> src, Attributes dst)
   {
      for (Map.Entry<String, String> property : src.entrySet())
      {
         dst.setString(property.getKey(), property.getValue());
      }
   }

   public static String getOwnerType(ObjectType<? extends Site> siteType)
   {
      if (siteType == ObjectType.PORTAL_SITE)
      {
         return PortalConfig.PORTAL_TYPE;
      }
      else if (siteType == ObjectType.GROUP_SITE)
      {
         return PortalConfig.GROUP_TYPE;
      }
      else if (siteType == ObjectType.USER_SITE)
      {
         return PortalConfig.USER_TYPE;
      }
      else
      {
         throw new IllegalArgumentException("Invalid site type " + siteType);
      }
   }

   public static ObjectType<Site> parseSiteType(String ownerType)
   {
      if (ownerType.equals(PortalConfig.PORTAL_TYPE))
      {
         return ObjectType.PORTAL_SITE;
      }
      else if (ownerType.equals(PortalConfig.GROUP_TYPE))
      {
         return ObjectType.GROUP_SITE;
      }
      else if (ownerType.equals(PortalConfig.USER_TYPE))
      {
         return ObjectType.USER_SITE;
      }
      else
      {
         throw new IllegalArgumentException("Invalid owner type " + ownerType);
      }
   }
}
