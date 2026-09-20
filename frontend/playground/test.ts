type Await<T> = T extends Promise<infer R> ? Awaited<R> : T

declare function PromiseAll<T extends unknown[]>(value: T): Promise<{ [U in keyof T]: Await<T[U]> }>

const result = PromiseAll([
  Promise.resolve(1),
  Promise.resolve('hello'),
  42,
]);

// result 类型应为：
// Promise<[number, string, number]>



////////////
type TupleToUnion<T extends unknown[]> = T extends [infer F, ...infer R] ? F | TupleToUnion<R> : never

type A = TupleToUnion<[1, 2, 3]>;        // 1 | 2 | 3
type B = TupleToUnion<['a', 'b']>;      // 'a' | 'b'
type C = TupleToUnion<[]>;              // never

///////////////
type ExcludeNullish<T> = T extends [infer F, ...infer R] ?
  (F extends null | undefined ? ExcludeNullish<R> : [F, ...ExcludeNullish<R>]) :
  T extends null | undefined ? never : T

type A1 = ExcludeNullish<[1, null, 2, undefined]>;
// [1, 2]

type B1 = ExcludeNullish<null | string | undefined>;
// string


//////////
type KeyPath<T> = T extends object ? { [U in keyof T]:
  | `${string & U}`
  | `${string & U}.${KeyPath<T[U]>}`
}[keyof T] : never

type Obj = {
  a: number;
  b: {
    c: boolean;
    d: {
      e: string;
    };
  };
};

type P = KeyPath<Obj>;
// 'a' | 'b' | 'b.c' | 'b.d' | 'b.d.e'

////////////////
// 核心工具类型 ReadonlyKeys
type ReadonlyKeys<T> = {
  [K in keyof T]:
  // 对比：原类型字段 vs 移除readonly修饰符后的字段
  Equal<
    { [P in K]: T[P] },
    { -readonly [P in K]: T[P] }
  > extends true ? never : K
}[keyof T];

// 判断两个类型是否完全等价的工具类型 Equal
type Equal<A, B> =
  (<X>() => X extends A ? 1 : 2) extends (<X>() => X extends B ? 1 : 2)
  ? true
  : false;


type T = {
  readonly a: number;
  b: string;
};

type R = ReadonlyKeys<T>; // 'a'

////////////////
type Events = {
  login: { userId: number };
  logout: {};
};

type Bus = {
  on<K extends keyof Events>(
    event: K,
    handler: (payload: Events[K]) => void
  ): void;

  emit<K extends keyof Events>(
    event: K,
    payload: Events[K]
  ): void;
};

const bus: Bus = {
  on(event, handler) {
    // 注册监听
  },
  emit(event, payload) {
    // 触发事件
  },
};

bus.on('login', (payload) => {
  console.log(payload.userId);
});

bus.emit('login', { userId: 1 });

bus.emit('logout', {}); // ❌ 应报错

//////////////
type SuccessResponse = {
  ok: true;
  data: {
    id: number;
    name: string;
  };
};

type ErrorResponse = {
  ok: false;
  error: string;
};

type ApiResponse = SuccessResponse | ErrorResponse;

function fetchApi(path: string): ApiResponse {
  if (path === '/user') {
    return {
      ok: true,
      data: { id: 1, name: 'Alice' },
    };
  }

  return {
    ok: false,
    error: 'not found',
  };
}

const res1 = fetchApi('/user');
res1.data.name; // ✅

const res2 = fetchApi('/404');
res2.error; // ✅
res2.data;  // ❌ 不应存在

//////////////
type handlerType = {
  increment(state: object, payload: any): object,
  decrement(state: any, payload: any): object,
}

function createReducer(initialState: object, handlers: handlerType) {
  return function reducer(state = initialState, action: { type: string, payload: number }) {
    const handler = handlers[action.type];
    return handler ? handler(state, action.payload) : state;
  };
}

const reducer = createReducer({ count: 0 }, {
  increment(state, payload) {
    return { count: state.count + payload };
  },
  decrement(state, payload) {
    return { count: state.count - payload };
  },
});

reducer(undefined, { type: 'increment', payload: 1 });
reducer(undefined, { type: 'unknown', payload: 1 }); // ❌



////////////////从类型 T 中选出符合 K 的属性，构造一个新的类型。
interface Todo {
  title: string
  description: string
  completed: boolean
}

type MyPick<T, K extends keyof T> = { [key in K]: T[key] }

type TodoPreview = MyPick<Todo, 'title' | 'completed'>

const todo: TodoPreview = {
  title: 'Clean room',
  completed: false,
}


////////////////////

interface Todo {
  title: string
  description: string
  completed: boolean
}

type MyOmit<T, K extends keyof T> = MyPick<T, Exclude<keyof T, K>>

type TodoPreview1 = MyOmit<Todo, 'description' | 'title'>

const todo1: TodoPreview1 = {
  completed: false,
}

//////////////

interface Todo1 {
  title: string
  description: string
}

type MyReadonly<T> = { readonly [key in keyof T]: T[key] }

const todo2: MyReadonly<Todo1> = {
  title: "Hey",
  description: "foobar"
}

todo2.title = "Hello" // Error: cannot reassign a readonly property
todo2.description = "barFoo" // Error: cannot reassign a readonly property


////////////////////11 元组转换为对象
const tuple = ['tesla', 'model 3', 'model X', 'model Y'] as const

type TupleToObject<T extends readonly PropertyKey[]> = { [key in T[number]]: key }

type result = TupleToObject<typeof tuple> // expected { 'tesla': 'tesla', 'model 3': 'model 3', 'model X': 'model X', 'model Y': 'model Y'}

///////////////10 元组转合集  返回元组所有的值
type Arr = ['1', '2', 3]

type TupleToUnion1<T> = T extends [infer F, ...infer E] ? F | TupleToUnion1<E> : never
type TupleToUnion2<T extends any[]> = T[number]
type TupleToUnion3<T> = T extends Array<infer U> ? U : never;
type Test = TupleToUnion3<Arr> // expected to be '1' | '2' | '3'

////////////////3188 Tuple to Nested Object
type TupleToNestedObject<T, U> = T extends [infer F extends PropertyKey, ...infer E] ? { [K in F]: TupleToNestedObject<E, U> } : U

type a = TupleToNestedObject<['a'], string> // {a: string}
type b = TupleToNestedObject<['a', 'b'], number> // {a: {b: number}}
type c = TupleToNestedObject<[], boolean> // boolean. if the tuple is empty, just return the U type



//////////472 Tuple to Enum Object
type IndexOf<
  T extends readonly unknown[],
  SearchItem,
  Acc extends number[] = []
> = T extends readonly [infer First, ...infer Rest]
  ? First extends SearchItem
  ? Acc["length"]
  : IndexOf<Rest, SearchItem, [...Acc, Acc["length"]]>
  : never;

type Enum<T extends readonly PropertyKey[], U = unknown> = U extends true ? { readonly [key in T[number]]: IndexOf<T, key> } : { readonly [key in T[number]]: key }
type aa = Enum<['macOS', 'Windows', 'Linux']>
// -> { readonly MacOS: "macOS", readonly Windows: "Windows", readonly Linux: "Linux" }
type bb = Enum<['macOS', 'Windows', 'Linux'], true>
// -> { readonly MacOS: 0, readonly Windows: 1, readonly Linux: 2 }


////////////8 对象部分属性只读

interface Todo {
  title: string
  description: string
  completed: boolean
}

type MyReadonly2<T, U extends keyof T> = Readonly<Pick<T, U>> & Omit<T, U>

const todo8: MyReadonly2<Todo, 'title' | 'description'> = {
  title: "Hey",
  description: "foobar",
  completed: false,
}

todo8.title = "Hello" // Error: cannot reassign a readonly property
todo8.description = "barFoo" // Error: cannot reassign a readonly property
todo8.completed = true // OK
