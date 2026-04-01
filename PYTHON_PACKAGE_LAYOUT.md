```
project/
  app/
    __init__.py
    scriptwhereiwanttoimport.py
    child/
      __init__.py
      iwanttoimportthisasmodule.py
```


```python
from app.child.iwanttoimportthisasmodule import my_function
```
