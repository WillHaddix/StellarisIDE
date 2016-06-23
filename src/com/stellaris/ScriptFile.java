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

import com.stellaris.script.ScriptBoolean;
import com.stellaris.script.ScriptColor;
import com.stellaris.script.ScriptFloat;
import com.stellaris.script.ScriptHSVColor;
import com.stellaris.script.ScriptInteger;
import com.stellaris.script.ScriptList;
import com.stellaris.script.ScriptRGBColor;
import com.stellaris.script.ScriptRange;
import com.stellaris.script.ScriptReference;
import com.stellaris.script.ScriptString;
import com.stellaris.script.ScriptStruct;
import com.stellaris.script.ScriptValue;
import com.stellaris.test.Debug;
import static com.stellaris.test.Debug.DEBUG;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.EmptyStackException;
import java.util.List;
import java.util.Stack;
import javax.script.*;

/**
 * Generates AST (a.k.a abstract syntax tree) for Stellaris Script
 *
 * @author donizyo
 */
public class ScriptFile {

    private ScriptParser parser;
    private boolean isCore;
    private ScriptValue cache;

    public static ScriptFile newInstance(File file) {
        return newInstance(file, null);
    }

    public static ScriptFile newInstance(File file, ScriptContext context) {
        try {
            return new ScriptFile(file, context);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static ScriptFile newInstance(Reader reader, ScriptContext context) {
        return new ScriptFile(reader, context);
    }

    private static boolean isCoreFile(File file) {
        Stellaris main;
        File root;

        main = Stellaris.getDefault();
        root = main.getRootDirectory();
        return isCoreFile(file, root);
    }

    private static boolean isCoreFile(File file, File root) {
        String proot, pfile;

        proot = root.getPath();
        pfile = file.getPath();
        return pfile.startsWith(proot);
    }

    private ScriptFile(ScriptParser parser, boolean isCoreFile, ScriptContext context) {
        this.parser = parser;
        this.isCore = isCoreFile;
        analyze(context);
    }

    private ScriptFile(File file, ScriptContext context) throws IOException {
        this(new ScriptParser(file), isCoreFile(file), context);
    }

    private ScriptFile(Reader reader, ScriptContext context) {
        this(new ScriptParser(reader), false, context);
    }

    private void analyze(ScriptContext context) {
        int res;

        try {
            res = analyze(null, 0, context);
            if (res != 0) {
                throw new AssertionError(res);
            }
        } finally {
            parser.close();
            parser = null;
        }
    }

    public void put(ScriptContext context, Field field, ScriptValue value) {
        Bindings bindings;
        Field parent;
        String fieldName;

        bindings = getBindings(context);
        if (bindings == null) {
            throw new NullPointerException();
        }
        parent = field.getParent();
        fieldName = field.getName();
        if (parent != null) {
            bindings = (ScriptStruct) get(context, parent);
        }
        bindings.put(fieldName, value);
    }

    public ScriptValue get(ScriptContext context, Field field) {
        Stack<String> stack;
        Field parent;
        String name;
        Bindings bindings;
        Object obj;
        ScriptValue value;

        if (field == null) {
            throw new NullPointerException();
        }
        stack = new Stack<>();
        parent = field;
        while (parent != null) {
            name = parent.getName();
            parent = parent.getParent();
            stack.push(name);
        }
        // root node
        try {
            name = stack.pop();
        } catch (EmptyStackException ex) {
            throw new AssertionError(ex);
        }
        bindings = getBindings(context);
        if (bindings == null) {
            throw new NullPointerException();
        }
        obj = bindings.get(name);
        if (obj == null) {
            throw new NullPointerException();
        }
        if (!(obj instanceof ScriptValue)) {
            throw new AssertionError(obj.getClass());
        }
        value = (ScriptValue) obj;
        // leaf node
        while (!stack.isEmpty()) {
            name = stack.pop();
            try {
                bindings = (ScriptStruct) value;

                obj = bindings.get(name);
                if (obj == null) {
                    if (stack.isEmpty()) {
                        return null;
                    } else {
                        throw new NullPointerException();
                    }
                }
                value = (ScriptValue) obj;
            } catch (NullPointerException ex) {
                throw new AssertionError(ex);
            } catch (ClassCastException ex) {
                throw new AssertionError(value.getClass().toString(), ex);
            }
        }

        return value;
    }

    private int analyze(Field parent, int state, ScriptContext context) {
        String token, key;
        List<String> tokens;
        Field field;
        //Type type;
        boolean isList;
        Patterns patterns;
        int newstate;
        int min, max;

        if (DEBUG) {
            System.err.format("[PARSE]\tparent=%s, state=%d%n",
                    parent, state
            );
        }
        while (parser.hasNext()) {
            token = parser.next();
            // ignore comment token
            if (token.charAt(0) == '#') {
                continue;
            }
            // return
            if ("}".equals(token)) {
                put(context, parent, cache);
                return --state;
            }
            {
                if (handleColorList(token)) {
                    //type = Type.COLORLIST;
                    put(context, parent, cache);
                    cache = null;
                    return --state;
                } else {
                    key = token;
                }

                // operator
                // or list?
                token = parser.next();
                isList = !"=".equals(token)
                        && !">".equals(token)
                        && !"<".equals(token);

                if (isList) {
                    // list entries: key, token, ...
                    cache = new ScriptList<>(
                            ScriptValue.parseString(key)
                    );
                    if (handlePlainList(token)) {
                        //type = Type.LIST;
                        //put(parent, type);
                        put(context, parent, cache);
                        cache = null;
                        return --state;
                    } else {
                        throw new AssertionError();
                    }
                } else {
                    field = new Field(parent, key);
                    // value
                    token = parser.next();
                    patterns = checkColorToken(token);
                    if (patterns != null) {
                        //type = 
                        handleColorToken(patterns);
                        put(context, field, cache);
                        cache = null;
                    } else if ("{".equals(token)) {
                        tokens = parser.peek(7);
                        // { -> min = INTEGER max = INTEGER }
                        if (Patterns.PS_RANGE.matches(tokens)) {
                            //type = Type.RANGE;
                            //parser.discard(7);
                            // minimal value
                            parser.discard(2);
                            token = parser.next();
                            min = Integer.parseInt(token);
                            // maximal value
                            parser.discard(2);
                            token = parser.next();
                            max = Integer.parseInt(token);
                            put(context, field, new ScriptRange(min, max));
                        } else {
                            // add 1 each time a struct is found
                            put(context, field, new ScriptStruct());
                            newstate = analyze(field, state + 1, context);
                            if (newstate != state) {
                                throw new AssertionError(String.format("old_state=%d, new_state=%d", state, newstate));
                            }
                            state = newstate;
                            /*
                            type = get(field);
                            if (type != Type.LIST) {
                                type = Type.STRUCT;
                            } else {
                                // skip binding procedure
                                type = null;
                            }
                             */
                        }
                    } else if ("yes".equals(token)) {
                        put(context, field, new ScriptBoolean(true));
                    } else if ("no".equals(token)) {
                        //type = Type.BOOLEAN;
                        put(context, field, new ScriptBoolean(false));
                    } else {
                        try {
                            // integer
                            put(context, field, new ScriptInteger(Integer.parseInt(token)));
                            //type = Type.INTEGER;
                        } catch (NumberFormatException e1) {
                            // float
                            try {
                                put(context, field, new ScriptFloat(Float.parseFloat(token)));
                                //type = Type.FLOAT;
                            } catch (NumberFormatException e2) {
                                if (token.startsWith("\"")
                                        && token.endsWith("\"")) {
                                    //type = Type.STRING;
                                    put(context, field, new ScriptString(token));
                                } else {
                                    //type = Type.VARIABLE;
                                    put(context, field, new ScriptReference(token));
                                }
                            }
                        }
                    }
                }

                // field - type binding
                //put(field, type);
            }
        }

        return state;
    }

    private Bindings getBindings(ScriptContext context) {
        Bindings bindings;

        if (isCore) {
            bindings = context.getBindings(ScriptContext.GLOBAL_SCOPE);
        } else {
            bindings = context.getBindings(ScriptContext.ENGINE_SCOPE);
            if (bindings == null) {
                context.getBindings(ScriptContext.GLOBAL_SCOPE);
            }
        }

        return bindings;
    }

    private boolean handleColorList(String token) {
        final int len = 5;
        Patterns patterns;
        ScriptColor color;
        ScriptList<ScriptColor> colorList;

        // detect color list
        patterns = checkColorToken(token);
        if (patterns == null) {
            return false;
        }

        colorList = new ScriptList<>();
        // handle color list
        while (true) {
            color = handleColorToken(patterns);
            colorList.add(color);
            // prevent loitering
            parser.discard(len);

            token = parser.next();
            patterns = checkColorToken(token);
            if (patterns != null) {
                continue;
            }
            switch (token) {
                case "}":
                    // exit color list
                    break;
                default:
                    throw new TokenException(token);
            }
            // exit color list
            break;
        }

        cache = colorList;
        return true;
    }

    private ScriptColor handleColorToken(Patterns patterns) {
        final int len = 5;
        List<String> tokens;
        String[] data;
        List<String> output;
        //Type type;
        ScriptColor color;
        int r, g, b;
        float h, s, v;

        if (patterns == null) {
            throw new NullPointerException();
        }
        tokens = parser.peek(len);
        data = new String[len];
        output = new ArrayList<>(len);
        if (patterns.matches(tokens, output)) {
            //type = Type.COLOR;
            output.toArray(data);
            if (patterns == Patterns.PS_COLOR_RGB) {
                r = Integer.parseInt(data[1]);
                g = Integer.parseInt(data[2]);
                b = Integer.parseInt(data[3]);
                color = new ScriptRGBColor(r, g, b);
            } else /*if (patterns == Patterns.PS_COLOR_HSV)*/ {
                h = Float.parseFloat(data[1]);
                s = Float.parseFloat(data[2]);
                v = Float.parseFloat(data[3]);
                color = new ScriptHSVColor(h, s, v);
            }
            parser.discard(len);
        } else {
            throw new TokenException("Color token exception");
        }
        //return type;
        return color;
    }

    private Patterns checkColorToken(String token) {
        Patterns patterns;

        switch (token) {
            case "hsv":
                patterns = Patterns.PS_COLOR_HSV;
                break;
            case "rgb":
                patterns = Patterns.PS_COLOR_RGB;
                break;
            default:
                patterns = null;
                break;
        }
        return patterns;
    }

    private boolean handlePlainList(String token) {
        ScriptList<ScriptValue> list;

        // handle single-element list
        if ("}".equals(token)) {
            return true;
        }

        list = (ScriptList<ScriptValue>) cache;
        list.add(ScriptValue.parseString(token));
        // handle multiple-element list
        while (true) {
            token = parser.next();
            if ("}".equals(token)) {
                return true;
            }
            if ("{".equals(token)
                    || "yes".equals(token)
                    || "no".equals(token)) {
                throw new TokenException(token);
            }
            list.add(ScriptValue.parseString(token));
        }
    }

    public static void main(String[] args) {
        String sparent, sname;
        File dir;
        File file;

        if (args.length < 2) {
            return;
        }
        Debug.DEBUG = true;
        sparent = args[0];
        sname = args[1];
        dir = new File(sparent);
        file = new File(dir, sname);
        System.out.format("Parsing file \"%s\"...%n", sname);
        ScriptFile.newInstance(file, null);
    }
}
