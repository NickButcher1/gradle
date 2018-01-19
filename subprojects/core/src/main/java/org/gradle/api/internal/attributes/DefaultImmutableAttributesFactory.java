/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.attributes;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.internal.changedetection.state.isolation.Isolatable;
import org.gradle.api.internal.changedetection.state.isolation.IsolatableFactory;
import org.gradle.api.internal.model.NamedObjectInstantiator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DefaultImmutableAttributesFactory implements ImmutableAttributesFactory {
    private final ImmutableAttributes root;
    private final Map<ImmutableAttributes, List<DefaultImmutableAttributes>> children;
    private final IsolatableFactory isolatableFactory;
    private NamedObjectInstantiator instantiator;

    public DefaultImmutableAttributesFactory(IsolatableFactory isolatableFactory, NamedObjectInstantiator instantiator) {
        this.isolatableFactory = isolatableFactory;
        this.instantiator = instantiator;
        this.root = ImmutableAttributes.EMPTY;
        this.children = Maps.newHashMap();
        children.put(root, new ArrayList<DefaultImmutableAttributes>());
    }

    public int size() {
        return children.size();
    }

    @Override
    public AttributeContainerInternal mutable() {
        return new DefaultMutableAttributeContainer(this, instantiator);
    }

    @Override
    public AttributeContainerInternal mutable(AttributeContainerInternal parent) {
        return new DefaultMutableAttributeContainer(this, parent, instantiator);
    }

    @Override
    public <T> ImmutableAttributes of(Attribute<T> key, T value) {
        return concat(root, key, value);
    }

    @Override
    public <T> ImmutableAttributes concat(ImmutableAttributes node, Attribute<T> key, T value) {
        return doConcatIsolatable(node, key, isolatableFactory.isolate(value));
    }

    @Override
    public <T> ImmutableAttributes concat(ImmutableAttributes node, Attribute<T> key, Isolatable<T> value) {
        return doConcatIsolatable(node, key, value);
    }

    private ImmutableAttributes doConcatIsolatable(ImmutableAttributes node, Attribute<?> key, Isolatable<?> value) {
        synchronized (this) {
            List<DefaultImmutableAttributes> nodeChildren = children.get(node);
            if (nodeChildren == null) {
                nodeChildren = Lists.newArrayList();
                children.put(node, nodeChildren);
            }
            for (DefaultImmutableAttributes child : nodeChildren) {
                if (child.attribute.equals(key) && child.value.equals(value)) {
                    return child;
                }
            }
            DefaultImmutableAttributes child = new DefaultImmutableAttributes((DefaultImmutableAttributes) node, key, value);
            nodeChildren.add(child);
            return child;
        }
    }

    public ImmutableAttributes getRoot() {
        return root;
    }

    @Override
    public ImmutableAttributes concat(ImmutableAttributes attributes1, ImmutableAttributes attributes2) {
        ImmutableAttributes current = attributes2;
        for (Attribute attribute : attributes1.keySet()) {
            if (!current.contains(attribute)) {
                if (attributes1 instanceof DefaultImmutableAttributes) {
                    current = doConcatIsolatable(current, attribute, ((DefaultImmutableAttributes) attributes1).getIsolatableAttribute(attribute));
                } else {
                    current = concat(current, attribute, attributes1.getAttribute(attribute));
                }
            }
        }
        return current;
    }
}
