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
package com.stellaris.script;

import com.stellaris.Type;
import javax.script.ScriptEngine;

/**
 *
 * @author donizyo
 */
public class ScriptString extends ScriptValue {

    private String strValue;

    public ScriptString(String value) {
        super();
        strValue = value;
    }

    public void set(String newValue) {
        strValue = newValue;
    }

    protected Type getType() {
        return Type.STRING;
    }

    public String get() {
        return strValue;
    }
}
