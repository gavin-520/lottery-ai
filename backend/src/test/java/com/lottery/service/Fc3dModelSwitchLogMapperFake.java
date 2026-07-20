package com.lottery.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lottery.entity.Fc3dModelSwitchLogEntity;
import com.lottery.mapper.Fc3dModelSwitchLogMapper;
import org.apache.ibatis.session.ResultHandler;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Sprint 10-E: trivial in-memory stand-in for {@link Fc3dModelSwitchLogMapper} — see {@link Fc3dModelRegistryMapperFake}. */
public class Fc3dModelSwitchLogMapperFake implements Fc3dModelSwitchLogMapper {

    private final List<Fc3dModelSwitchLogEntity> rows = new ArrayList<>();
    private final AtomicLong sequence = new AtomicLong(1);

    @Override
    public int insert(Fc3dModelSwitchLogEntity entity) {
        entity.setId(sequence.getAndIncrement());
        rows.add(entity);
        return 1;
    }

    @Override
    public int updateById(Fc3dModelSwitchLogEntity entity) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).getId().equals(entity.getId())) {
                rows.set(i, entity);
                return 1;
            }
        }
        return 0;
    }

    @Override
    public List<Fc3dModelSwitchLogEntity> selectList(Wrapper<Fc3dModelSwitchLogEntity> queryWrapper) {
        return new ArrayList<>(rows);
    }

    @Override
    public Long selectCount(Wrapper<Fc3dModelSwitchLogEntity> queryWrapper) {
        return (long) rows.size();
    }

    private UnsupportedOperationException unused() {
        return new UnsupportedOperationException("not used by Fc3dModelRegistryService");
    }

    @Override
    public int deleteById(Serializable id) {
        throw unused();
    }

    @Override
    public int deleteById(Fc3dModelSwitchLogEntity entity) {
        throw unused();
    }

    @Override
    public int delete(Wrapper<Fc3dModelSwitchLogEntity> queryWrapper) {
        throw unused();
    }

    @Override
    public int deleteBatchIds(Collection<?> idList) {
        throw unused();
    }

    @Override
    public int update(Fc3dModelSwitchLogEntity entity, Wrapper<Fc3dModelSwitchLogEntity> updateWrapper) {
        throw unused();
    }

    @Override
    public Fc3dModelSwitchLogEntity selectById(Serializable id) {
        throw unused();
    }

    @Override
    public List<Fc3dModelSwitchLogEntity> selectBatchIds(Collection<? extends Serializable> idList) {
        throw unused();
    }

    @Override
    public void selectBatchIds(Collection<? extends Serializable> idList, ResultHandler<Fc3dModelSwitchLogEntity> resultHandler) {
        throw unused();
    }

    @Override
    public void selectList(Wrapper<Fc3dModelSwitchLogEntity> queryWrapper, ResultHandler<Fc3dModelSwitchLogEntity> resultHandler) {
        throw unused();
    }

    @Override
    public List<Fc3dModelSwitchLogEntity> selectList(IPage<Fc3dModelSwitchLogEntity> page, Wrapper<Fc3dModelSwitchLogEntity> queryWrapper) {
        throw unused();
    }

    @Override
    public void selectList(IPage<Fc3dModelSwitchLogEntity> page, Wrapper<Fc3dModelSwitchLogEntity> queryWrapper,
                           ResultHandler<Fc3dModelSwitchLogEntity> resultHandler) {
        throw unused();
    }

    @Override
    public List<Map<String, Object>> selectMaps(Wrapper<Fc3dModelSwitchLogEntity> queryWrapper) {
        throw unused();
    }

    @Override
    public void selectMaps(Wrapper<Fc3dModelSwitchLogEntity> queryWrapper, ResultHandler<Map<String, Object>> resultHandler) {
        throw unused();
    }

    @Override
    public List<Map<String, Object>> selectMaps(IPage<? extends Map<String, Object>> page, Wrapper<Fc3dModelSwitchLogEntity> queryWrapper) {
        throw unused();
    }

    @Override
    public void selectMaps(IPage<? extends Map<String, Object>> page, Wrapper<Fc3dModelSwitchLogEntity> queryWrapper,
                           ResultHandler<Map<String, Object>> resultHandler) {
        throw unused();
    }

    @Override
    public <E> List<E> selectObjs(Wrapper<Fc3dModelSwitchLogEntity> queryWrapper) {
        throw unused();
    }

    @Override
    public <E> void selectObjs(Wrapper<Fc3dModelSwitchLogEntity> queryWrapper, ResultHandler<E> resultHandler) {
        throw unused();
    }
}
