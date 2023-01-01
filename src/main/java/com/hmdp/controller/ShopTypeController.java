package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IShopTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥            店铺类型 酒吧 美食
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    @GetMapping("list")
    public Result queryTypeList() {
 /*       List<ShopType> typeList = typeService         直接查询数据库   List<ShopType> list集合也就是多个实体类
                .query().orderByAsc("sort").list();
        return Result.ok(typeList);                            */
        //使用的list操作数据转换
        return typeService.selectAllShopType();      //ctrl alt b 查看实现类的代码

    }
}
