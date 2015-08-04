# coding=utf-8
"""
.NET (CLR) specific functions
"""
__author__ = 'Ilya.Kazakevich'

IS_CLR = False
try:
    import clr
    IS_CLR = hasattr(clr, 'AddReference')
except ImportError:
    pass

IS_PYTHON_DOT_NET = False
try:
    import Python.Runtime
except ImportError:
    pass
else:
    IS_PYTHON_DOT_NET = True
    clr.setPreload(True)

def get_namespace_by_name(object_name):
    """
    Gets namespace for full object name. Sometimes last element of name is module while it may be class.
    For System.Console returns System, for System.Web returns System.Web.
    Be sure all required assemblies are loaded (i.e. clr.AddRef.. is called)
    :param object_name: name to parse
    :return: namespace
    """
    (imported_object, object_name) = _import_first(object_name)
    parts = object_name.partition(".")
    first_part = parts[0]
    remain_part = parts[2]

    while remain_part and type(_get_attr_by_name(imported_object, remain_part)) is type:  # While we are in class
        remain_part = remain_part.rpartition(".")[0]

    if remain_part:
        return first_part + "." + remain_part
    else:
        return first_part


def _import_first(object_name):
    """
    Some times we can not import module directly. For example, Some.Class.InnerClass could not be imported: you need to import "Some.Class"
    or even "Some" instead. This function tries to find part of name that could be loaded

     :param object_name: name in dotted notation like "Some.Function.Here"
     :return: (imported_object, object_name): tuple with object and its name
    """
    while object_name:
        try:
            return (__import__(object_name, globals=[], locals=[], fromlist=[]), object_name)
        except ImportError:
            object_name = object_name.rpartition(".")[0]  # Remove rightest part
    raise Exception("No module name found in name " + object_name)


def _get_attr_by_name(obj, name):
    """
    Accepts chain of attributes in dot notation like "some.property.name" and gets them on object
    :param obj: object to introspec
    :param name: attribute name
    :return attribute

    >>> str(_get_attr_by_name("A", "__class__.__class__"))
    "<type 'type'>"

    >>> str(_get_attr_by_name("A", "__class__.__len__.__class__"))
    "<type 'method_descriptor'>"
    """
    result = obj
    parts = name.split('.')
    for part in parts:
        result = getattr(result, part)
    return result

def get_assembly_namespaces(assembly_name, assembly_path):
    from System.Reflection import ReflectionTypeLoadException
    try:
        assembly = clr.AddReference(assembly_name)
        types = assembly.GetTypes()
    except ReflectionTypeLoadException:
        return []
    else:
        return sorted(set([str(type.Namespace) for type in types ]))


if IS_PYTHON_DOT_NET:

    def get_applicable_assemblies(module_name, _namespace_to_assembly = {}):
        if not _namespace_to_assembly:
            for assembly in clr.ListAssemblies(False):
                namespaces = get_assembly_namespaces(assembly ,clr.FindAssembly(assembly))
                for namespace in namespaces:
                    curr_lst = _namespace_to_assembly.get(namespace, [])
                    curr_lst.append(assembly)
                    _namespace_to_assembly[namespace] =  curr_lst

        return _namespace_to_assembly.get(module_name, [])


    from System import Type
    def get_clr_type(wrapped_type):
        type_name = None
        try:
            type_name = wrapped_type.__module__ + "." + wrapped_type.__name__
        except AttributeError:
            raise TypeError("No such CLR type " + str(wrapped_type))


        type = Type.GetType(type_name)
        if type:
            return type

        for assembly in get_applicable_assemblies(wrapped_type.__module__ ):
            type = Type.GetType(type_name + "," + assembly)
            if type:
                break

        if not type:
            raise TypeError("No such CLR type " + str(wrapped_type) + "name " + type_name)
        return type


else:
    def get_clr_type(wrapped_type):
        return  clr.GetClrType(wrapped_type)


if IS_PYTHON_DOT_NET:
    from System import String
    def is_clr_type(clr_type):
        if not clr_type: return False
        if not type(String) == type(clr_type):
            return False
        try:
            get_clr_type(clr_type)
        except TypeError:
            return False
        else:
            return True
else:
    def is_clr_type(clr_type):
        if not clr_type: return False
        try:
            clr.GetClrType(clr_type)
            return True
        except TypeError:
            return False

def is_clr_assembly(mod_name):
    return True if clr.FindAssembly(mod_name) else False

