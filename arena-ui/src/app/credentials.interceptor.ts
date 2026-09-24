import { HttpInterceptorFn } from '@angular/common/http';

export const withCredentialsInterceptor: HttpInterceptorFn = (req, next) => {
    // Ensure that cookies are sent with every API request
    if (req.url.startsWith('/api')) {
        const credReq = req.clone({
            withCredentials: true
        });
        return next(credReq);
    }
    return next(req);
};
