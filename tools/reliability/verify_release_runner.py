#!/usr/bin/env python3
"""Check the actual DEX ABI shared by the minified app and AndroidJUnitRunner.

AGP can exclude an app dependency from the test APK even when R8 removed its
unused entry points. Checking dependency resolution or mapping.txt alone misses
that failure. No device, decompiler, signing key or third-party Python package.
"""
import argparse
import copy
import struct
import zipfile

TRACE = 'Landroidx/tracing/Trace;'
RUNNER = 'Landroidx/test/runner/AndroidJUnitRunner;'
FUTURE = 'Landroidx/concurrent/futures/CallbackToFutureAdapter;'


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


class Dex:
    def __init__(self, data):
        self.data = data
        if not data.startswith(b'dex\n') or data[40:44] != b'\x78\x56\x34\x12':
            raise ValueError('Unsupported DEX header')
        def table(offset, width):
            count, start = struct.unpack_from('<II', data, offset)
            return [start + width*i for i in range(count)]
        self.strings = []
        for pos in table(56, 4):
            start = self.u32(pos)
            _, start = self.uleb(start)
            end = data.index(b'\0', start)
            self.strings.append(data[start:end].decode('utf-8', errors='replace'))
        self.types = [self.strings[self.u32(p)] for p in table(64, 4)]
        self.parents = {}
        protos = []
        for pos in table(72, 12):
            params = self.u32(pos+8)
            args = [] if not params else [self.types[struct.unpack_from('<H', data, params+4+2*i)[0]]
                                          for i in range(self.u32(params))]
            protos.append('(' + ''.join(args) + ')' + self.types[self.u32(pos+4)])
        self.methods = []
        self.fields = []
        for pos in table(80, 8):
            owner, kind, name = struct.unpack_from('<HHI', data, pos)
            self.fields.append((self.types[owner], self.strings[name], self.types[kind]))
        for pos in table(88, 8):
            owner, proto, name = struct.unpack_from('<HHI', data, pos)
            self.methods.append((self.types[owner], self.strings[name], protos[proto]))
        self.classes, self.definitions, self.field_definitions = set(), set(), set()
        for pos in table(96, 32):
            owner = self.types[self.u32(pos)]
            self.classes.add(owner)
            parent = self.u32(pos+8)
            parents = [] if parent == 0xffffffff else [self.types[parent]]
            interfaces = self.u32(pos+12)
            if interfaces:
                parents += [self.types[struct.unpack_from('<H', data, interfaces+4+2*i)[0]]
                            for i in range(self.u32(interfaces))]
            self.parents[owner] = parents
            cursor = self.u32(pos+24)
            if not cursor:
                continue
            sizes = []
            for _ in range(4):
                value, cursor = self.uleb(cursor)
                sizes.append(value)
            for count in sizes[:2]:
                index = 0
                for _ in range(count):
                    diff, cursor = self.uleb(cursor)
                    index += diff
                    _, cursor = self.uleb(cursor)
                    self.field_definitions.add(self.fields[index])
            for count in sizes[2:]:
                index = 0
                for _ in range(count):
                    diff, cursor = self.uleb(cursor)
                    index += diff
                    _, cursor = self.uleb(cursor)  # access flags
                    _, cursor = self.uleb(cursor)  # code offset
                    self.definitions.add(self.methods[index])

    def u32(self, pos):
        return struct.unpack_from('<I', self.data, pos)[0]

    def uleb(self, pos):
        value, shift = 0, 0
        while True:
            b = self.data[pos]
            pos += 1
            value |= (b & 127) << shift
            if b < 128:
                return value, pos
            shift += 7
            if shift > 35:
                raise ValueError('Malformed ULEB128')


def apk(path):
    with zipfile.ZipFile(path) as archive:
        return [Dex(archive.read(n)) for n in archive.namelist()
                if n.startswith('classes') and n.endswith('.dex') and '/' not in n]


def validate(app, tests):
    require(app and tests, 'APK has no DEX files')
    require(any(RUNNER in dex.classes for dex in tests), 'Instrumentation runner missing')
    classes = {c for dex in app + tests for c in dex.classes}
    missing_owners = sorted({m[0] for dex in tests for m in dex.methods
                            if m[0].startswith(('Landroidx/', 'Lcom/transiva/app/', 'Lorg/webrtc/', 'Lkotlin/', 'Lkotlinx/', 'Lcom/google/common/'))} - classes)
    require(not missing_owners, 'Test APK references missing classes: ' + ', '.join(missing_owners))
    app_classes = {c for dex in app for c in dex.classes}
    platform = ('Landroid/', 'Ljava/', 'Ljavax/', 'Ldalvik/', 'Lsun/')
    references = {m for dex in tests for m in dex.methods
                  if not m[0].startswith(platform) and (m[0] in app_classes
                      or m[0] in (TRACE, FUTURE) or m[0].startswith(('Lkotlin/', 'Lkotlinx/')))}
    require(any(m[0] == TRACE for m in references), 'Runner no longer references Trace; review this ABI check')
    # Also require the known startup entry point even if test packaging changes.
    references.add((TRACE, 'forceEnableAppTracing', '()V'))
    definitions = {m for dex in app + tests for m in dex.definitions}
    parents = {c: ps for dex in app + tests for c, ps in dex.parents.items()}
    def resolves(method, members, seen=None):
        if method in members:
            return True
        owner, name, proto = method
        # Constructors are never inherited. Kotlin multifile facade methods are.
        if name == '<init>':
            return False
        seen = set() if seen is None else seen
        if owner in seen:
            return False
        seen.add(owner)
        return any(resolves((parent,name,proto),members,seen) for parent in parents.get(owner,[]))
    # Android/Java boot-class implementations are supplied by the device. Some
    # SDKs package empty Android compatibility stubs, which must not be treated
    # as the boot implementation. An app method inherited from a platform parent
    # is left to the emulator gate; constructors are never inherited.
    def external_parent(owner, seen=None):
        if owner == 'Ljava/lang/Object;':
            return False
        if owner.startswith(platform):
            return True
        seen = set() if seen is None else seen
        if owner in seen:
            return False
        seen.add(owner)
        return any(external_parent(p,seen) for p in parents.get(owner,[]))
    object_methods = {
        ('equals','(Ljava/lang/Object;)Z'), ('hashCode','()I'),
        ('toString','()Ljava/lang/String;'), ('getClass','()Ljava/lang/Class;'),
        ('clone','()Ljava/lang/Object;'), ('finalize','()V'),
        ('wait','()V'), ('wait','(J)V'), ('wait','(JI)V'),
        ('notify','()V'), ('notifyAll','()V')}
    missing = sorted(m for m in references if not resolves(m,definitions)
                     and (m[1],m[2]) not in object_methods
                     and (m[1] == '<init>' or not external_parent(m[0])))
    require(not missing, 'Missing shared runner ABI: ' + ', '.join(str(m) for m in missing))
    fields = {f for dex in tests for f in dex.fields
              if not f[0].startswith(platform) and (f[0] in app_classes or f[0].startswith(('Lkotlin/','Lkotlinx/')))}
    field_definitions = {f for dex in app + tests for f in dex.field_definitions}
    missing_fields = sorted(f for f in fields if not resolves(f,field_definitions) and not external_parent(f[0]))
    require(not missing_fields, 'Missing shared fields: ' + ', '.join(str(f) for f in missing_fields))
    return references


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('app_apk')
    parser.add_argument('test_apk')
    parser.add_argument('--self-test', action='store_true', help='Also prove missing shared APIs are rejected, using copies of these DEX tables')
    args = parser.parse_args()
    app, tests = apk(args.app_apk), apk(args.test_apk)
    references = validate(app, tests)
    print(f'PASS release runner linkage: {len(references)} shared method references checked through DEX hierarchy; shared fields checked (platform inheritance deferred to device gate)')
    if args.self_test:
        for label, member_set, remove in [
                ('Trace startup method', 'definitions', lambda m:m[0] == TRACE and m[1] == 'forceEnableAppTracing'),
                ('Kotlin inherited lazy', 'definitions', lambda m:m[0].startswith('Lkotlin/LazyKt') and m[1] == 'lazy'),
                ('Kotlin field', 'field_definitions', lambda m:m[0] == 'Lkotlin/jvm/internal/StringCompanionObject;' and m[1] == 'INSTANCE'),
                ('Coroutine constructor', 'definitions', lambda m:m[0] == 'Lkotlin/coroutines/jvm/internal/SuspendLambda;' and m[1] == '<init>')]:
            altered = [copy.copy(dex) for dex in app]
            removed = 0
            for dex in altered:
                original = getattr(dex,member_set)
                retained = {m for m in original if not remove(m)}
                removed += len(original)-len(retained)
                setattr(dex,member_set,retained)
            require(removed > 0, 'Self-test fixture API changed: ' + label)
            try:
                validate(altered, tests)
            except RuntimeError:
                print('PASS preflight rejects missing ' + label)
            else:
                raise RuntimeError('Preflight accepted missing ' + label)


if __name__ == '__main__':
    main()
