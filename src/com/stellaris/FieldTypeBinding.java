/*
 * Copyright (C) 2016 donizyo
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.stellaris;

import com.stellaris.script.*;
import java.io.PrintStream;
import java.util.*;
import javax.script.*;

/**
 *
 * @author donizyo
 */
public class FieldTypeBinding {

    private final Map<String, ScriptValue> map;

    public FieldTypeBinding(ScriptContext context) {
        Bindings bindings;

        map = new TreeMap<>();
        bindings = context.getBindings(ScriptContext.GLOBAL_SCOPE);
        loadFromMemory(bindings);
    }

    private void loadFromMemory(Bindings bindings) {
        Set<String> keySet;
        Object obj;
        ScriptValue value;

        keySet = bindings.keySet();
        for (String key : keySet) {
            obj = bindings.get(key);
            if (obj == null) {
                continue;
            }
            if (!(obj instanceof ScriptValue)) {
                continue;
            }
            value = (ScriptValue) obj;
            map.put(key, value);
            if (value instanceof ScriptStruct) {
                loadFromMemory((ScriptStruct) value);
            }
        }
    }

    public void list(PrintStream out) {
        Set<String> keySet;
        ScriptValue value;
        Set<Type> set;
        ScriptStruct struct;
        Set<String> children;

        keySet = map.keySet();
        for (String key : keySet) {
            value = map.get(key);
            set = value.getTypeSet();
            if (value instanceof ScriptStruct) {
                struct = (ScriptStruct) value;
                children = struct.getChildren();
                out.format("%s=%s{%n",
                        key, set);
                for (String child : children) {
                    out.format("\t%s%n", child);
                }
                out.format("}%n");
            } else {
                out.format("%s=%s%n",
                        key, set);
            }
        }
    }
}
