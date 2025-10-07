

Kebutuhan 1 : user bisa login

Post /auth/login
Request Body 1 - email:
```
{
    "login_method" : 1
    "email" : "naufal.sunandar@bni.co.id",
    "Password" : "admin"
}
```


Request Body 2 - email:
```
{
    "login_method" : 2
    "email" : "0812345678",
    "Password" : "admin"
}

```


Response Body :
```
{
    "status": "success"
    "error" :{
        "reason" : ""
        }
}
```


